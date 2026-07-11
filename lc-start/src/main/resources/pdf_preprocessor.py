import argparse
from pathlib import Path

import fitz  # PyMuPDF
import cv2
import numpy as np
import PIL.Image as Image

from scipy.ndimage import zoom, percentile_filter, rotate
from numpy import amin, amax

# https://github.com/jiangnanboy/Doc-Image-Tool/tree/main

def remove_red_stamp_keep_text(bgr: np.ndarray,
                               red_diff_thresh: int = 30,
                               red_min_r: int = 80,
                               protect_gray_thresh: int = 80,
                               morph_ksize: int = 3) -> np.ndarray:
    """
    思路（针对红章覆盖黑字的常见扫描件）：
    1) 用 redness = R - max(B, G) 强化“红色相对优势”
    2) threshold 得到红章候选区域 mask
    3) 只在“较亮的区域”把 mask 内像素抬成白色（255），避免把黑字笔画抹掉
       - 黑字通常灰度值更低，因此用 protect_gray_thresh 保护
    输出：去红后的灰度图（uint8）
    """
    b, g, r = cv2.split(bgr)
    max_bg = cv2.max(b, g)
    redness = cv2.subtract(r, max_bg)  # 更“红”的地方值更大

    _, mask1 = cv2.threshold(redness, red_diff_thresh, 255, cv2.THRESH_BINARY)
    _, mask2 = cv2.threshold(r, red_min_r, 255, cv2.THRESH_BINARY)
    mask = cv2.bitwise_and(mask1, mask2)

    # 形态学：去噪 + 填洞，让 mask 更连贯
    k = max(1, int(morph_ksize))
    if k % 2 == 0:
        k += 1
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k))
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel, iterations=1)
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, kernel, iterations=2)

    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)

    # 关键：只“漂白”那些本来就不暗的区域（更像红章底色/纸张），保护黑字笔画
    to_whiten = (mask == 255) & (gray > protect_gray_thresh)
    out = gray.copy()
    out[to_whiten] = 255

    # 轻微对比度拉伸（可选；对 OCR 往往有帮助）
    out = cv2.normalize(out, None, 0, 255, cv2.NORM_MINMAX)

    return out


def resize_im(im, scale, max_scale=None):
    f = float(scale) / min(im.shape[0], im.shape[1])
    if max_scale != None and f * max(im.shape[0], im.shape[1]) > max_scale:
        f = float(max_scale) / max(im.shape[0], im.shape[1])
    return cv2.resize(im, (0, 0), fx=f, fy=f)


def estimate_skew_angle(raw, angleRange=[-15, 15]):
    raw = resize_im(raw, scale=600, max_scale=900)
    image = raw - amin(raw)
    image = image / amax(image)
    m = zoom(image, 0.5)
    m = percentile_filter(m, 80, size=(20, 2))
    m = percentile_filter(m, 80, size=(2, 20))
    m = zoom(m, 1.0 / 0.5)
    # w,h = image.shape[1],image.shape[0]
    w, h = min(image.shape[1], m.shape[1]), min(image.shape[0], m.shape[0])
    flat = np.clip(image[:h, :w] - m[:h, :w] + 1, 0, 1)
    d0, d1 = flat.shape
    o0, o1 = int(0.1 * d0), int(0.1 * d1)
    flat = amax(flat) - flat
    flat -= amin(flat)
    est = flat[o0:d0 - o0, o1:d1 - o1]
    angles = range(angleRange[0], angleRange[1])
    estimates = []
    for a in angles:
        roest = rotate(est, a, order=0, mode='constant')
        v = np.mean(roest, axis=1)
        v = np.var(v)
        estimates.append((v, a))

    _, a = max(estimates)
    return a


def eval_angle(img, angleRange=[-5, 5]):
    im = Image.fromarray(img)
    degree = estimate_skew_angle(np.array(im.convert('L')), angleRange=angleRange)
    im = im.rotate(degree, center=(im.size[0] / 2, im.size[1] / 2), expand=1, fillcolor=255)
    img = np.array(im)
    return img, degree


def sauvola_threshold(image, window_size=15, k=0.2, r=128):
    """
    Sauvola自适应阈值化算法，用于图像二值化处理
    适用于OCR预处理，能够根据局部区域特征动态调整阈值
    """
    # 将图像转换为灰度图
    if len(image.shape) > 2:
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    else:
        gray = image

    # 计算图像的平均值和标准差
    mean = cv2.blur(gray, (window_size, window_size))
    mean_square = cv2.blur(gray * gray, (window_size, window_size))
    std = np.sqrt(mean_square - mean * mean)

    # 计算阈值
    threshold = mean * (1 + k * (std / r - 1))

    # 阈值化图像
    binary = np.zeros_like(gray)
    binary[gray > threshold] = 255

    return binary


def process_pdf(input_pdf: Path,
                output_pdf: Path,
                dpi: int = 300,
                red_diff_thresh: int = 30,
                red_min_r: int = 80,
                protect_gray_thresh: int = 80,
                morph_ksize: int = 3,
                correct_skew: bool = True,
                apply_sauvola: bool = False,
                sauvola_window: int = 15,
                sauvola_k: float = 0.2,
                sauvola_r: int = 128):
    if not input_pdf.exists():
        raise FileNotFoundError(f"找不到输入文件: {input_pdf}")

    zoom = dpi / 72.0
    mat = fitz.Matrix(zoom, zoom)

    doc = fitz.open(str(input_pdf))
    out_doc = fitz.open()

    for idx, page in enumerate(doc, start=1):
        pix = page.get_pixmap(matrix=mat, alpha=False)

        # PyMuPDF 的 samples 通常是 RGB 顺序
        img = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, pix.n)

        if pix.n == 3:
            bgr = cv2.cvtColor(img, cv2.COLOR_RGB2BGR)
        elif pix.n == 4:
            bgr = cv2.cvtColor(img, cv2.COLOR_RGBA2BGR)
        else:
            # 罕见情况：先按 RGB 兜底
            bgr = img[:, :, :3]
            bgr = cv2.cvtColor(bgr, cv2.COLOR_RGB2BGR)

        gray_out = remove_red_stamp_keep_text(
            bgr,
            red_diff_thresh=red_diff_thresh,
            red_min_r=red_min_r,
            protect_gray_thresh=protect_gray_thresh,
            morph_ksize=morph_ksize
        )

        # 文档校正：检测和校正倾斜角度
        if correct_skew:
            corrected_img, degree = eval_angle(gray_out)
            print(f"Page {idx}: corrected skew by {degree:.2f} degrees")
            gray_out = corrected_img

        # Sauvola自适应阈值化：提升文字与背景对比度
        if apply_sauvola:
            binary_out = sauvola_threshold(gray_out, window_size=sauvola_window, k=sauvola_k, r=sauvola_r)
            print(f"Page {idx}: applied Sauvola thresholding")
            gray_out = binary_out

        # 写回 PDF 需要彩色通道，这里把灰度转成 RGB
        rgb_out = cv2.cvtColor(gray_out, cv2.COLOR_GRAY2RGB)
        ok, buf = cv2.imencode(".png", rgb_out)
        if not ok:
            raise RuntimeError(f"第 {idx} 页编码 PNG 失败")

        # 新建同尺寸页面并铺满插图
        new_page = out_doc.new_page(width=page.rect.width, height=page.rect.height)
        new_page.insert_image(new_page.rect, stream=buf.tobytes())

        print(f"Processed page {idx}/{len(doc)}")

    out_doc.save(str(output_pdf), deflate=True)
    out_doc.close()
    doc.close()


def main():
    parser = argparse.ArgumentParser(description="PDF 红章去除/弱化预处理（提升 OCR）")
    parser.add_argument("--input", default="test.pdf", help="输入 PDF（默认 test.pdf）")
    parser.add_argument("--output", default="test_processed.pdf", help="输出 PDF（默认 test_processed.pdf）")
    parser.add_argument("--dpi", type=int, default=300, help="渲染 DPI（默认 300）")

    # 红色检测与文字保护参数（你可按样本调参）
    parser.add_argument("--red-diff-thresh", type=int, default=10,
                        help="redness=R-max(B,G) 的阈值，越小越“敏感”（默认 30）")
    parser.add_argument("--red-min-r", type=int, default=80,
                        help="候选红色区域要求 R 通道最小值（默认 80）")
    parser.add_argument("--protect-gray-thresh", type=int, default=80,
                        help="保护较暗区域（疑似文字笔画），只有灰度>该值才漂白（默认 80）")
    parser.add_argument("--morph-ksize", type=int, default=3,
                        help="mask 形态学核大小（默认 3，可试 5/7）")
    parser.add_argument("--correct-skew", action="store_true", default=True,
                        help="启用文档倾斜校正（默认启用）")
    parser.add_argument("--no-correct-skew", action="store_false", dest="correct_skew",
                        help="禁用文档倾斜校正")
    parser.add_argument("--apply-sauvola", action="store_true", default=True,
                        help="启用Sauvola自适应阈值化（默认禁用）")
    parser.add_argument("--sauvola-window", type=int, default=15,
                        help="Sauvola窗口大小（默认15）")
    parser.add_argument("--sauvola-k", type=float, default=0.2,
                        help="Sauvola k参数，越小阈值越保守（默认0.2）")
    parser.add_argument("--sauvola-r", type=int, default=128,
                        help="Sauvola r参数，动态范围参考值（默认128）")

    args = parser.parse_args()

    process_pdf(
        input_pdf=Path(args.input),
        output_pdf=Path(args.output),
        dpi=args.dpi,
        red_diff_thresh=args.red_diff_thresh,
        red_min_r=args.red_min_r,
        protect_gray_thresh=args.protect_gray_thresh,
        morph_ksize=args.morph_ksize,
        correct_skew=args.correct_skew,
        apply_sauvola=args.apply_sauvola,
        sauvola_window=args.sauvola_window,
        sauvola_k=args.sauvola_k,
        sauvola_r=args.sauvola_r
    )

    print(f"Done: {args.output}")


if __name__ == "__main__":
    main()
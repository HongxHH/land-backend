package com.gov.landcheck.project.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 地块住宅/商业面积计算器。
 * 根据总面积与商住比计算住宅面积与商业面积。
 *
 * @author system
 */
public final class LandParcelAreaCalculator {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private LandParcelAreaCalculator() {
    }

    /**
     * 根据总面积与商住比拆分住宅/商业面积。
     * <p>
     * 商住比字段含义：商住比 = 商业占比（例如 0.6 表示商业 60%，住宅 40%）。
     * </p>
     *
     * @param totalArea 地块总面积，可为 null（视为 0）
     * @param commercialResidentialRatio 商业占比（0~1），可为 null（视为 0）
     * @return 住宅面积与商业面积，不会为 null
     */
    public static AreaResult compute(BigDecimal totalArea, BigDecimal commercialResidentialRatio) {
        BigDecimal t = totalArea != null ? totalArea : BigDecimal.ZERO;
        BigDecimal r = commercialResidentialRatio != null ? commercialResidentialRatio : BigDecimal.ZERO;

        // r: commercial share. residential = total - commercial.
        if (r.compareTo(BigDecimal.ZERO) < 0 || r.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("商住比必须在[0,1]区间（0.6 表示商业占比60%）");
        }

        BigDecimal commercialArea = t.multiply(r).setScale(SCALE, ROUNDING);
        BigDecimal residentialArea = t.subtract(commercialArea).setScale(SCALE, ROUNDING);
        return new AreaResult(residentialArea, commercialArea);
    }

    @Getter
    @AllArgsConstructor
    public static class AreaResult {
        private final BigDecimal residentialArea;
        private final BigDecimal commercialArea;
    }
}

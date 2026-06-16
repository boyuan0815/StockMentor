package net.boyuan.stockmentor.market.stockpricehistory.repository;

import java.math.BigDecimal;

public interface IntradayDayRangeProjection {
    BigDecimal getHighPrice();

    BigDecimal getLowPrice();
}

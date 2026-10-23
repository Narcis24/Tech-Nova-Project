package com.neueda.app.services;

import com.neueda.app.dtos.FundamentalsResponse;
import com.neueda.app.dtos.FundamentalsResponse.FinancialPeriod;
import com.neueda.app.models.InstrumentFinancial;
import com.neueda.app.models.InstrumentFinancial.PeriodType;
import com.neueda.app.models.InstrumentProfile;
import com.neueda.app.repositories.InstrumentFinancialRepository;
import com.neueda.app.repositories.InstrumentProfileRepository;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Company and fund facts the data pipeline stored, for the trade page. */
@Service
public class FundamentalsService {

    private final InstrumentProfileRepository profileRepository;
    private final InstrumentFinancialRepository financialRepository;

    public FundamentalsService(InstrumentProfileRepository profileRepository,
                               InstrumentFinancialRepository financialRepository) {
        this.profileRepository = profileRepository;
        this.financialRepository = financialRepository;
    }

    /** Empty when the pipeline has not loaded the symbol (yet). */
    public Optional<FundamentalsResponse> getFundamentals(String symbol) {
        return profileRepository.findById(symbol).map(profile -> {
            List<InstrumentFinancial> periods = financialRepository.findBySymbolOrderByPeriodTypeAscPeriodEndAsc(symbol);
            return response(profile, periods(periods, PeriodType.QUARTERLY), periods(periods, PeriodType.ANNUAL));
        });
    }

    private static List<FinancialPeriod> periods(List<InstrumentFinancial> all, PeriodType type) {
        return all.stream()
            .filter(f -> f.getPeriodType() == type)
            .map(f -> new FinancialPeriod(f.getPeriodEnd(), f.getRevenue(), f.getGrossProfit(), f.getOperatingIncome(),
                f.getNetIncome(), f.getEpsDiluted()))
            .toList();
    }

    private static FundamentalsResponse response(InstrumentProfile p, List<FinancialPeriod> quarters, List<FinancialPeriod> years) {
        return new FundamentalsResponse(
            p.getSymbol(), p.getQuoteType(), p.getSector(), p.getIndustry(), p.getCategory(), p.getFundFamily(),
            p.getDescription(), p.getWebsite(), p.getEmployees(), p.getMarketCap(), p.getTotalAssets(),
            p.getTrailingPe(), p.getForwardPe(), p.getEpsTtm(), p.getEpsForward(), p.getDividendYield(),
            p.getExpenseRatio(), p.getBeta(), p.getRevenueTtm(), p.getGrossProfitTtm(), p.getProfitMargin(),
            p.getRevenueGrowth(), p.getUpdatedAt().toInstant(ZoneOffset.UTC), quarters, years);
    }
}

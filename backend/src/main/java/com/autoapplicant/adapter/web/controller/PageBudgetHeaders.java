package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.domain.document.PageBudget;
import java.util.List;
import org.springframework.http.HttpHeaders;

/**
 * The page budget of an exported PDF, sent as headers because the body is the PDF itself. A
 * browser on another origin reads them only when CORS exposes {@link #NAMES}.
 */
public final class PageBudgetHeaders {

    public static final String PAGE_COUNT = "X-Page-Count";
    public static final String BUDGET = "X-Page-Budget";
    public static final String OVER_BUDGET = "X-Page-Over-Budget";
    public static final List<String> NAMES = List.of(PAGE_COUNT, BUDGET, OVER_BUDGET);

    private PageBudgetHeaders() {}

    static void write(PageBudget budget, HttpHeaders headers) {
        headers.set(PAGE_COUNT, String.valueOf(budget.pageCount()));
        headers.set(BUDGET, String.valueOf(budget.budget()));
        headers.set(OVER_BUDGET, String.valueOf(budget.overBudget()));
    }
}

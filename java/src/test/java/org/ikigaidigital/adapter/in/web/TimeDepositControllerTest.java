package org.ikigaidigital.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.ikigaidigital.application.TimeDepositView;
import org.ikigaidigital.application.WithdrawalView;
import org.ikigaidigital.application.port.in.ListTimeDepositsUseCase;
import org.ikigaidigital.application.port.in.UpdateAllBalancesUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TimeDepositController.class)
class TimeDepositControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ListTimeDepositsUseCase listTimeDeposits;

    @MockitoBean
    private UpdateAllBalancesUseCase updateAllBalances;

    @Test
    void listsDepositsWithWithdrawalsAndTotal() throws Exception {
        given(listTimeDeposits.listAll())
                .willReturn(List.of(
                        new TimeDepositView(
                                1,
                                "basic",
                                new BigDecimal("1234.56"),
                                45,
                                List.of(new WithdrawalView(7, new BigDecimal("100.00"), LocalDate.of(2026, 10, 1)))),
                        new TimeDepositView(2, "premium", new BigDecimal("1000.00"), 46, List.of())));

        mvc.perform(get("/api/v1/time-deposits"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "items": [
                            { "id": 1, "planType": "basic", "balance": 1234.56, "days": 45,
                              "withdrawals": [ { "id": 7, "amount": 100.00, "date": "2026-10-01" } ] },
                            { "id": 2, "planType": "premium", "balance": 1000.00, "days": 46, "withdrawals": [] }
                          ],
                          "total": 2
                        }
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void listsNoDeposits() throws Exception {
        given(listTimeDeposits.listAll()).willReturn(List.of());

        mvc.perform(get("/api/v1/time-deposits"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        { "items": [], "total": 0 }
                        """));
    }

    @Test
    void updatesBalancesAndReturnsNoContent() throws Exception {
        mvc.perform(put("/api/v1/time-deposits/balances"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(updateAllBalances).should().updateAllBalances();
    }

    @Test
    void updateIsNotAvailableWithPost() throws Exception {
        mvc.perform(post("/api/v1/time-deposits/balances")).andExpect(status().isMethodNotAllowed());
    }
}

package org.ikigaidigital.adapter.in.web;

import java.util.List;
import org.ikigaidigital.adapter.in.web.api.TimeDepositsApi;
import org.ikigaidigital.adapter.in.web.api.model.TimeDepositDto;
import org.ikigaidigital.adapter.in.web.api.model.TimeDepositPageDto;
import org.ikigaidigital.adapter.in.web.api.model.WithdrawalDto;
import org.ikigaidigital.application.TimeDepositView;
import org.ikigaidigital.application.WithdrawalView;
import org.ikigaidigital.application.port.in.ListTimeDepositsUseCase;
import org.ikigaidigital.application.port.in.UpdateAllBalancesUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** REST adapter for the two endpoints of the OpenAPI contract ({@code openapi/time-deposits.yaml}). */
@RestController
class TimeDepositController implements TimeDepositsApi {

    private final ListTimeDepositsUseCase listTimeDeposits;
    private final UpdateAllBalancesUseCase updateAllBalances;

    TimeDepositController(ListTimeDepositsUseCase listTimeDeposits, UpdateAllBalancesUseCase updateAllBalances) {
        this.listTimeDeposits = listTimeDeposits;
        this.updateAllBalances = updateAllBalances;
    }

    @Override
    public ResponseEntity<TimeDepositPageDto> listTimeDeposits() {
        List<TimeDepositDto> items = listTimeDeposits.listAll().stream()
                .map(TimeDepositController::toDto)
                .toList();
        return ResponseEntity.ok(new TimeDepositPageDto(items, (long) items.size()));
    }

    @Override
    public ResponseEntity<Void> updateBalances() {
        updateAllBalances.updateAllBalances();
        return ResponseEntity.noContent().build();
    }

    private static TimeDepositDto toDto(TimeDepositView view) {
        return new TimeDepositDto(
                view.id(),
                view.planType(),
                view.balance(),
                view.days(),
                view.withdrawals().stream().map(TimeDepositController::toDto).toList());
    }

    private static WithdrawalDto toDto(WithdrawalView view) {
        return new WithdrawalDto(view.id(), view.amount(), view.date());
    }
}

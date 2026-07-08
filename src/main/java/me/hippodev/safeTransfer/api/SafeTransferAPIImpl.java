package me.hippodev.safeTransfer.api;

import me.hippodev.safeTransfer.transfer.TransferService;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

final class SafeTransferAPIImpl implements SafeTransferAPI {

    private final TransferService transferService;

    SafeTransferAPIImpl(TransferService transferService) {
        this.transferService = transferService;
    }

    @Override
    public CompletableFuture<TransferOutcome> transfer(Collection<? extends Player> targets, String address, int delayTicks) {
        return transferService.transferSilently(targets, address, delayTicks);
    }
}

package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.reminder.FollowUpReminder;
import com.autoapplicant.port.in.reminder.ManageFollowUpRemindersUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Follow-up Reminders")
public class FollowUpReminderController {

    public record CreateReminderRequest(String note, Instant dueAt) {}

    private final ManageFollowUpRemindersUseCase reminders;
    private final SecurityContextHelper secCtx;

    public FollowUpReminderController(ManageFollowUpRemindersUseCase reminders,
                                      SecurityContextHelper secCtx) {
        this.reminders = reminders;
        this.secCtx = secCtx;
    }

    @Operation(summary = "List all open (uncompleted) follow-up reminders")
    @GetMapping("/api/v1/reminders")
    public ResponseEntity<List<FollowUpReminder>> getOpen() {
        return ResponseEntity.ok(reminders.getOpenReminders(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "List follow-up reminders due within 24 hours")
    @GetMapping("/api/v1/reminders/due")
    public ResponseEntity<List<FollowUpReminder>> getDue() {
        return ResponseEntity.ok(reminders.getDueReminders(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "List reminders for an application")
    @GetMapping("/api/v1/applications/{applicationId}/reminders")
    public ResponseEntity<List<FollowUpReminder>> listForApplication(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(reminders.getForApplication(applicationId, secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Create a follow-up reminder for an application")
    @ApiResponse(responseCode = "201", description = "Reminder created")
    @PostMapping("/api/v1/applications/{applicationId}/reminders")
    public ResponseEntity<FollowUpReminder> create(@PathVariable UUID applicationId,
                                                    @RequestBody CreateReminderRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        FollowUpReminder saved = reminders.createReminder(applicationId, userId, req.note(), req.dueAt());
        return ResponseEntity.created(URI.create("/api/v1/reminders/" + saved.id())).body(saved);
    }

    @Operation(summary = "Mark a reminder as completed")
    @PostMapping("/api/v1/reminders/{id}/complete")
    public ResponseEntity<FollowUpReminder> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(reminders.completeReminder(id, secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Delete a reminder")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/api/v1/reminders/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        reminders.deleteReminder(id, secCtx.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}

package org.keyfort.demosite.service;

import org.keyfort.core.Crypto;
import org.keyfort.demosite.entity.TicketEntity;
import org.keyfort.demosite.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final WebhookDispatchService webhookService;
    private final Clock clock;

    @Autowired
    public TicketService(TicketRepository ticketRepository, WebhookDispatchService webhookService) {
        this(ticketRepository, webhookService, Clock.systemUTC());
    }

    public TicketService(TicketRepository ticketRepository, WebhookDispatchService webhookService, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.webhookService = webhookService;
        this.clock = clock;
    }

    @Transactional
    public TicketEntity createTicket(String entryId, String scope, Duration ttl) {
        Objects.requireNonNull(entryId, "entryId must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");

        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);

        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(Crypto.random(10));
        String token = "tkt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(Crypto.random(16));

        TicketEntity ticket = new TicketEntity(id, token, entryId, scope, now, expiresAt);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public void revokeTicket(String id) {
        Objects.requireNonNull(id, "id must not be null");
        ticketRepository.findById(id).ifPresent(t -> {
            t.setRevoked(true);
            ticketRepository.save(t);
        });
    }

    @Transactional(readOnly = true)
    public TicketEntity validateTicket(String token) {
        Objects.requireNonNull(token, "token must not be null");
        TicketEntity ticket = ticketRepository.findByTicketToken(token)
                .orElseThrow(() -> new TicketValidationException("Invalid or expired login ticket"));

        Instant now = clock.instant();
        if (ticket.isUsed() || ticket.isRevoked() || !now.isBefore(ticket.getExpiresAt())) {
            throw new TicketValidationException("Invalid or expired login ticket");
        }
        return ticket;
    }

    @Transactional
    public TicketEntity consumeTicket(String token) {
        Objects.requireNonNull(token, "token must not be null");
        TicketEntity ticket = ticketRepository.findByTicketToken(token)
                .orElseThrow(() -> new TicketValidationException("Invalid or expired login ticket"));

        Instant now = clock.instant();
        if (ticket.isUsed() || ticket.isRevoked() || !now.isBefore(ticket.getExpiresAt())) {
            throw new TicketValidationException("Invalid or expired login ticket");
        }

        ticket.setUsed(true);
        TicketEntity saved = ticketRepository.save(ticket);

        webhookService.dispatchLoginObserved(saved.getId(), saved.getEntryId(), saved.getScope());
        return saved;
    }
}

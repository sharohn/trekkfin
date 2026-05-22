package com.trekkfin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "from_id", nullable = false)
    private Account from;
    
    @ManyToOne
    @JoinColumn(name = "to_id", nullable = false)
    private Account to;
    
    @Column(nullable = false)
    private BigDecimal amount;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime created;
    
    private LocalDateTime updated;
    
    @PrePersist
    protected void onCreate() {
        created = LocalDateTime.now();
        updated = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updated = LocalDateTime.now();
    }
}


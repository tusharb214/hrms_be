package com.finabits.hrms.dto.response;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LeaveBalanceResponse {
    private int    totalAllowed;
    private double usedPaid;       // double — supports 0.5 for half days
    private double usedUnpaid;
    private double remainingPaid;
    private double totalUsed;
    private int    year;
}
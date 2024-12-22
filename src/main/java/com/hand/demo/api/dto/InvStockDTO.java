package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvStock;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class InvStockDTO extends InvStock {
    private BigDecimal summary;
    private boolean isLot;
}

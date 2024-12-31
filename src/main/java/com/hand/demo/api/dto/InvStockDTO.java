package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvStock;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.Transient;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
public class InvStockDTO extends InvStock {
    private BigDecimal summary;
    private boolean isLot;
    private List<Long> materialsId;
    private List<Long> batchIds;
}

package com.ecommerce.api.product.dto;

import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.List;

public record ReplaceProductImagesReq(
        List<@Valid ImageIdWithOrder> imageIdList
) {
    public ReplaceProductImagesReq {
        if (imageIdList == null) imageIdList = new ArrayList<>();
    }
}

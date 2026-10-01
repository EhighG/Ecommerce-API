package com.ecommerce.api.product.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.media.entity.UploadedImage;
import com.ecommerce.api.media.service.MediaService;
import com.ecommerce.api.product.dto.ImageIdWithOrder;
import com.ecommerce.api.product.dto.ReplaceProductImagesReq;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static com.ecommerce.api.support.UnitTestFixtures.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImageOwnershipTest {

    @Mock
    ProductRepository productRepository;

    @Mock
    MediaService mediaService;

    @Mock
    ProductDeletionService productDeletionService;

    @InjectMocks
    ProductService productService;

    @Test
    @DisplayName("남이 올린 이미지를 상품에 붙이면 없는 이미지와 같은 404 오류다")
    void replaceProductImages_withOthersImage_throwsNotFound() {
        Long sellerId = 2L;
        Product product = product();
        ReflectionTestUtils.setField(product.getSeller(), "id", sellerId);
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(product));

        UploadedImage othersImage = UploadedImage.builder()
                .objectKey("product_images/20260930/other.png")
                .uploadUserId(99L)
                .contentType("image/png")
                .fileSize(100L)
                .build();
        ReflectionTestUtils.setField(othersImage, "id", 5L);
        when(mediaService.getUploadedImages(List.of(5L))).thenReturn(List.of(othersImage));

        ReplaceProductImagesReq req = new ReplaceProductImagesReq(List.of(new ImageIdWithOrder(5L, 1)));

        AppException exception = assertThrows(AppException.class,
                () -> productService.replaceProductImages(10L, req, sellerId));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UPLOADED_IMAGE_NOT_FOUND);
        assertThat(exception.getErrorCode().httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

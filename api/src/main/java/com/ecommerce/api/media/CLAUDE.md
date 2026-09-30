# media — 이미지 업로드(GCS)

## 담당 범위
- 담당하는 것:
  - 서명 업로드 URL 발급, 업로드 완료 등록(`UploadedImage`), 공개 URL 계산(`resolveUrl`)
  - 이미지 사용 여부 표시(`attached`)
  - 저장소 추상화 `ObjectStorageClient`(구현체 `GcsClient`)
- 담당하지 않는 것:
  - 상품에 이미지를 붙이거나 떼는 판단(product). 여기서는 조회와 `detachAllById`만 제공한다.
  - GCS 객체 삭제. 인터페이스에 `delete`가 있지만 아무도 호출하지 않는다.
  - 이미지 리사이즈나 검수

## 항상 지켜야 할 것
- 이미지 바이트는 API를 거치지 않는다. 클라이언트가 서명 URL(V4, PUT, 10분, `Content-Type` 고정)로 GCS에 직접 올린다.
- `contentType`은 발급할 때와 완료 등록할 때(실제 GCS 객체의 형식) 모두 `image/`로 시작해야 한다(`8001`).
- 업로드 파일은 10MB 이하다. 현재는 서명 URL과 완료 등록 모두 크기를 검사하지 않는다(`docs/tracking/findings/product.md`).
- 객체 경로는 `{uploadPrefix}/{yyyyMMdd(서울)}/{UUID}{소문자 확장자}`다. 원본 파일명은 확장자 말고는 쓰지 않는다.
- 완료 등록은 멱등이다. 같은 경로를 같은 사람이 다시 등록하면 기존 레코드를 돌려주고, 다른 사람이면 `IMAGE_OWNER_MISMATCH`다.
  - 아직 등록되지 않은 경로는 경로를 아는 판매자라면 누구나 등록할 수 있다. UUID라 추측하기 어려우므로 허용한 수준이다.
- DB에는 object key만 저장한다. 공개 URL은 조회할 때마다 `publicBaseUrl + "/" + 경로 인코딩된 key`로 만든다. 버킷 공개 설정이나 기본 URL이 바뀌어도 데이터를 옮길 필요가 없게 하기 위해서다.

## 알아둘 구현 방식
- `GcsConfig`가 기동할 때 Application Default Credentials를 읽는다. 자격 증명이 없으면 이미지 기능을 쓰지 않더라도 서버가 뜨지 않는다. 통합 테스트는 `MediaService`를 mock으로 대체한다.
- `MediaService`는 클래스 전체가 `@Transactional`(쓰기)이고, 조회 메서드에만 `readOnly`를 붙였다.

## 테스트 기준
이 패키지를 바꾸면 `ObjectStorageClient`를 가짜로 만들어 아래를 확인한다.
- 이미지가 아닌 형식 거절(발급과 등록 모두)
- GCS에 객체가 없으면 404 `8000`
- 같은 사람이 재등록하면 같은 ID
- 다른 사람이 등록하면 `8002`
- 경로 형식(접두어가 비었을 때 포함)
- 공개 URL 인코딩(공백, 한글 key)

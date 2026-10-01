# media — 이미지 업로드(GCS)

업로드 절차와 규칙(형식, URL 유효시간, 크기, 완료 등록의 멱등·소유)은 `docs/business-rules.md`의 "이미지 업로드"에 있다.

## 담당하지 않는 것
- 상품에 이미지를 붙이거나 떼는 판단(product). 여기서는 조회와 `detachAllById`만 제공한다.
- 이미지 리사이즈나 검수

## 항상 지켜야 할 것
- 객체 경로는 `{uploadPrefix}/{yyyyMMdd(서울)}/{UUID}{소문자 확장자}`다. 원본 파일명은 확장자 말고는 쓰지 않는다.
- 같은 경로를 다른 사람이 다시 등록하면 404가 아니라 `IMAGE_OWNER_MISMATCH`(400)다. 이유는 `docs/security.md`의 404/403 기준에 있다.

## 알아둘 구현 방식
- 통합 테스트는 `MediaService`를 mock으로 대체한다. `GcsConfig`가 기동할 때 자격 증명을 읽기 때문이다(`docs/engineering-notes.md`).

## 테스트 기준
이 패키지를 바꾸면 `ObjectStorageClient`를 가짜로 만들어 아래를 확인한다.
- 이미지가 아닌 형식 거절(발급과 등록 모두)
- GCS에 객체가 없으면 404 `8000`
- 같은 사람이 재등록하면 같은 ID
- 다른 사람이 등록하면 `8002`
- 경로 형식(접두어가 비었을 때 포함)
- 공개 URL 인코딩(공백, 한글 key)

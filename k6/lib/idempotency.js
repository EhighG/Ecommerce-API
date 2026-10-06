import exec from "k6/execution";

// 주문 생성의 Idempotency-Key. 키는 주문서 하나에 하나다. 반복 한 번이 주문서 하나이므로 반복마다 새 키를 만든다.
// 결과를 모르는 재시도에는 같은 키를 다시 쓴다.
// 서버 형식: 1~128자, [A-Za-z0-9._:-] (docs/contracts.md "주문 생성의 멱등 키").
export function newIdempotencyKey(prefix = "k6") {
  let vu = 0;
  try {
    vu = exec.vu.idInTest;
  } catch (e) {
    // setup·teardown에서는 VU 정보가 없다.
  }
  const random = Math.random().toString(36).slice(2, 12);
  return `${prefix}-${vu}-${Date.now()}-${random}`;
}

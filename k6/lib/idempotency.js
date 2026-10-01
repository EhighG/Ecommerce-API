import exec from "k6/execution";

// 주문 생성의 Idempotency-Key. "주문하기" 한 번마다 새 키를 만들고, 같은 주문을 재시도할 때는 같은 키를 다시 쓴다.
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

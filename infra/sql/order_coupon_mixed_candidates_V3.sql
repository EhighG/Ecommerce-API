-- order_coupon_mixed_candidates - V3
set @target_total_rows := 37806;
set @target_coupon_rows := 7089;
set @target_normal_rows := @target_total_rows - @target_coupon_rows;
set @target_buyer_rows := 8000;
set @cart_items_per_buyer := 6;
set @target_cart_item_rows := @target_buyer_rows * @cart_items_per_buyer;
set @target_product_rows := 3000;
set @min_inventory_quantity := 40;

set foreign_key_checks = 0;
truncate coupon_issued;
truncate coupon_event;
set foreign_key_checks = 1;

insert ignore into cart_item (
  user_id,
  product_id,
  quantity
)
with
buyer_ranked as (
  select
    u.id as user_id,
    row_number() over (order by u.id) as buyer_rn
  from users u
  where u.role = 'BUYER'
    and u.deleted = false
    and u.id >= 10
),
buyer_pool as (
  select user_id, buyer_rn
  from buyer_ranked
  where buyer_rn <= @target_buyer_rows
),
product_ranked as (
  select
    p.id as product_id,
    row_number() over (order by p.id) as product_rn
  from product p
  join inventory i on i.product_id = p.id
  where p.deleted = false
    and i.quantity >= @min_inventory_quantity
),
product_pool as (
  select product_id, product_rn
  from product_ranked
  where product_rn <= @target_product_rows
),
product_count as (
  select count(*) as cnt
  from product_pool
),
seq as (
  select 1 as n union all
  select 2 union all
  select 3 union all
  select 4 union all
  select 5 union all
  select 6
),
candidate as (
  select
    b.user_id,
    p.product_id,
    row_number() over (order by b.buyer_rn, s.n) as rn
  from buyer_pool b
  join seq s
  join product_count pc
  join product_pool p
    on p.product_rn = ((b.buyer_rn * 37 + s.n * 101) % pc.cnt) + 1
)
select
  c.user_id,
  c.product_id,
  1
from candidate c
where c.rn <= @target_cart_item_rows;

insert into coupon_event (
    name,
    type,
    discount_value,
    max_discount_amount,
    initial_quantity,
    start_at,
    end_at,
    valid_seconds,
    active,
    created_at,
    updated_at
)
values (
    concat('선착순 ', @target_coupon_rows, '명 20% 할인(최대 5000원)'),
    'PERCENT',
    20,
    5000,
    @target_coupon_rows,
    utc_timestamp() - interval 1 hour,
    utc_timestamp() + interval 1 day,
    86400,
    true,
    utc_timestamp(),
    utc_timestamp()
);

insert into coupon_issued (
  coupon_event_id, user_id, status,
  issued_at, expires_at, used_at,
  created_at, updated_at
)
with eligible_users as (
  select
    u.id,
    row_number() over (order by u.id) as rn
  from users u
  where u.role = 'BUYER'
    and u.deleted = false
    and u.id >= 10
    and exists (
      select 1
      from cart_item ci
      join product p on p.id = ci.product_id
      join inventory i on i.product_id = p.id
      where ci.user_id = u.id
        and p.deleted = false
        and ci.quantity >= 1
        and i.quantity >= 1
    )
    and not exists (
      select 1
      from coupon_issued ci
      where ci.coupon_event_id = :couponEventId
        and ci.user_id = u.id
    )
)
select
  :couponEventId,
  eu.id,
  'ISSUED',
  current_timestamp,
  timestampadd(day, 1, current_timestamp),
  null,
  current_timestamp,
  current_timestamp
from eligible_users eu
where eu.rn <= @target_coupon_rows;

with coupon_source as (
  select
    u.email,
    u.id as userId,
    ci_cart.id as cartItemId,
    p.id as productId,
    1 as orderQuantity,
    ce.id as couponEventId,
    ci_coupon.id as couponIssueId,
    case when mod(ci_cart.id, 3) = 0 then 'true' else 'false' end as cancelAfterOrder,
    row_number() over (
      partition by ci_coupon.id
      order by mod(ci_cart.id * 17 + ci_coupon.id * 131, 1000003), ci_cart.id
    ) as coupon_rn,
    row_number() over (
      partition by ci_cart.id
      order by ci_coupon.id
    ) as cart_rn
  from coupon_issued ci_coupon
  join users u on u.id = ci_coupon.user_id
  join coupon_event ce on ce.id = ci_coupon.coupon_event_id
  join cart_item ci_cart on ci_cart.user_id = u.id
  join product p on p.id = ci_cart.product_id
  join inventory i on i.product_id = p.id
  where ci_coupon.coupon_event_id = :couponEventId
    and u.role = 'BUYER'
    and u.deleted = false
    and u.id >= 10
    and p.deleted = false
    and ci_cart.quantity >= 1
    and i.quantity >= 1
    and ci_coupon.status = 'ISSUED'
    and ci_coupon.expires_at > current_timestamp
),
coupon_ranked as (
  select
    email, userId, cartItemId, productId, orderQuantity,
    couponEventId, couponIssueId, cancelAfterOrder,
    row_number() over (order by couponIssueId) as rn
  from coupon_source
  where coupon_rn = 1
    and cart_rn = 1
),
coupon_rows as (
  select
    email, userId, cartItemId, productId, orderQuantity,
    couponEventId, couponIssueId, cancelAfterOrder, rn
  from coupon_ranked
  where rn <= @target_coupon_rows
),
normal_source as (
  select
    u.email,
    u.id as userId,
    ci.id as cartItemId,
    p.id as productId,
    1 as orderQuantity,
    null as couponEventId,
    null as couponIssueId,
    case when mod(ci.id, 5) = 0 then 'true' else 'false' end as cancelAfterOrder
  from cart_item ci
  join users u on u.id = ci.user_id
  join product p on p.id = ci.product_id
  join inventory i on i.product_id = p.id
  where u.role = 'BUYER'
    and u.deleted = false
    and u.id >= 10
    and p.deleted = false
    and ci.quantity >= 1
    and i.quantity >= 1
    and not exists (
      select 1
      from coupon_rows cr
      where cr.cartItemId = ci.id
    )
),
normal_ranked as (
  select
    email, userId, cartItemId, productId, orderQuantity,
    couponEventId, couponIssueId, cancelAfterOrder,
    row_number() over (
      order by mod(userId * 131 + cartItemId * 17, 1000003), cartItemId
    ) as rn
  from normal_source
),
normal_rows as (
  select
    email, userId, cartItemId, productId, orderQuantity,
    couponEventId, couponIssueId, cancelAfterOrder, rn
  from normal_ranked
  where rn <= @target_normal_rows
),
mixed_rows as (
  select
    email, userId, cartItemId, productId, orderQuantity,
    couponEventId, couponIssueId, cancelAfterOrder,
    floor((rn - 0.5) * @target_total_rows / @target_coupon_rows) as sort_bucket,
    0 as sort_type
  from coupon_rows

  union all

  select
    email, userId, cartItemId, productId, orderQuantity,
    couponEventId, couponIssueId, cancelAfterOrder,
    floor((rn - 0.5) * @target_total_rows / @target_normal_rows) as sort_bucket,
    1 as sort_type
  from normal_rows
)
select
  email,
  userId,
  cartItemId,
  productId,
  orderQuantity,
  coalesce(couponEventId, '') as couponEventId,
  coalesce(couponIssuedId, '') as couponIssuedId,
  cancelAfterOrder
from mixed_rows
order by sort_bucket, sort_type, cartItemId;

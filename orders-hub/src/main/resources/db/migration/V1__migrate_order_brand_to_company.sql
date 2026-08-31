insert into company (
    brand_name,
    primary_domain_name,
    logo_url,
    enrichment_status,
    is_active,
    created_at,
    updated_at
)
select distinct
    trim(o.brand_name),
    null,
    null,
    'PENDING',
    true,
    now(),
    now()
from orders o
where o.brand_name is not null
  and trim(o.brand_name) <> ''
  and not exists (
      select 1
      from company c
      where lower(c.brand_name) = lower(trim(o.brand_name))
  );

update orders o
join company c
  on lower(c.brand_name) = lower(trim(o.brand_name))
set o.company_id = c.id
where o.brand_name is not null
  and trim(o.brand_name) <> '';

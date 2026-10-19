-- Keep existing demo descriptions consistent with template browsing. Historical index data is left intact.
UPDATE pies AS p SET description = examples.description
FROM (VALUES
    ('d0000000-0000-4000-8000-000000000001'::uuid, 'Example mix of large technology companies with 5% cash.'),
    ('d0000000-0000-4000-8000-000000000002'::uuid, 'Example mix of dividend stocks with 10% cash.'),
    ('d0000000-0000-4000-8000-000000000003'::uuid, 'Example mix of stock funds, bonds and gold with 5% cash.')
) AS examples(pie_id, description)
WHERE p.pie_id = examples.pie_id AND p.owner_username = 'demo';


-- Create test user
INSERT INTO calendar_enrichment.users (email, calendar_token, timezone, created_at)
VALUES ('stephan@usergems.com', '7SS16U^PmxkdVJlb^', 'UTC', NOW());

-- Add person data
INSERT INTO calendar_enrichment.persons (
    email, first_name, last_name, title, linkedin_url, avatar_url,
    company_name, company_linkedin, company_employees, fetched_at
) VALUES (
    'john@algolia.com',
    'John',
    'Doe',
    'CEO',
    'https://linkedin.com/in/johndoe',
    'https://example.com/avatar.jpg',
    'Algolia',
    'https://linkedin.com/company/algolia',
    1000,
    NOW()
),
(
    'jane@algolia.com',
    'Jane',
    'Smith',
    'CTO',
    'https://linkedin.com/in/janesmith',
    'https://example.com/avatar2.jpg',
    'Algolia',
    'https://linkedin.com/company/algolia',
    1000,
    NOW()
),
(
    'bob@algolia.com',
    'Bob',
    'Johnson',
    'VP Engineering',
    'https://linkedin.com/in/bobjohnson',
    'https://example.com/avatar3.jpg',
    'Algolia',
    'https://linkedin.com/company/algolia',
    1000,
    NOW()
);

INSERT INTO calendar_enrichment.persons (
    email, first_name, last_name, title, linkedin_url, avatar_url,
    company_name, company_linkedin, company_employees, fetched_at
) VALUES (
    'christian@usergems.com',
    'Christian',
    'Kletzl',
    'CEO & Co-Founder',
    'https://linkedin.com/in/ckletzl',
    'https://example.com/avatar4.jpg',
    'UserGems',
    'https://linkedin.com/company/usergems',
    150,
    NOW()
),
(
    'stephan@usergems.com',
    'Stephan',
    'Ganocy',
    'CTO',
    'https://linkedin.com/in/sganocy',
    'https://example.com/avatar5.jpg',
    'UserGems',
    'https://linkedin.com/company/usergems',
    150,
    NOW()
);

INSERT INTO calendar_enrichment.persons (
    email, first_name, last_name, title, linkedin_url, avatar_url,
    company_name, company_linkedin, company_employees, fetched_at
) VALUES (
    'sarah@techcorp.com',
    'Sarah',
    'Williams',
    'Product Manager',
    'https://linkedin.com/in/sarahwilliams',
    'https://example.com/avatar6.jpg',
    'TechCorp',
    'https://linkedin.com/company/techcorp',
    5000,
    NOW()
),
(
    'mike@startup.io',
    'Mike',
    'Brown',
    'Software Engineer',
    'https://linkedin.com/in/mikebrown',
    'https://example.com/avatar7.jpg',
    'Startup Inc',
    'https://linkedin.com/company/startup',
    50,
    NOW()
);

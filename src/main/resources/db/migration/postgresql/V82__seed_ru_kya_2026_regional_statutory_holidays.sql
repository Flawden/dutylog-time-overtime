-- P1B2C3: first real reviewed regional statutory-holiday source pack.
-- RU-KYA / Krasnoyarsk Krai / calendar year 2026.
--
-- The reviewed regional positive fact set is empty.
-- Federal Article-112 holidays remain separate federal authority.
-- Transferred rest days are not statutory public-holiday identities.

INSERT INTO regional_statutory_holiday_datasets (
    jurisdiction_code,
    region_code,
    coverage_from,
    coverage_to,
    legal_regime,
    legal_basis,
    source_revision,
    source_reference,
    complete,
    fingerprint,
    source_pack_schema,
    source_pack_sha256,
    completeness_evidence
) VALUES (
    'RU',
    'RU-KYA',
    DATE '2026-01-01',
    DATE '2026-12-31',
    'RU_KYA_REGIONAL_STATUTORY_HOLIDAYS_2026_REVIEW_2026_09_04_V1',
    'TK_RF_ARTICLE_6;FEDERAL_LAW_125_FZ_ARTICLE_4_PART_7;SUPREME_COURT_RF_20_PV11',
    'CONSULTANTPLUS_REGIONAL_NONWORKING_HOLIDAY_REFERENCE_ACCESSED_2026_09_04',
    'https://www.consultant.ru/document/cons_doc_LAW_311098/;https://www.consultant.ru/document/cons_doc_LAW_34683/b5f8286871331a1188b20733154abe4957594b3b/;https://www.consultant.ru/document/cons_doc_LAW_16218/ca78a0f4594e9666e8259f2b87a4df2e59a38cb4/',
    TRUE,
    '3965ebb71bacbc610c799d81b96730399b0e0aba11779776fc9e58c608c27071',
    'DUTYLOG_REGIONAL_STATUTORY_HOLIDAY_SOURCE_PACK_V1',
    '7ca56e78cb7c5342af5b73ad59a0326daf88d34d69e561e1825aaaa2ac3be9c3',
    'ConsultantPlus current regional non-working holiday reference, accessed 2026-09-04, states that it presents non-working (holiday) days established by normative acts of RF subjects; its subject list does not include Krasnoyarsk Krai. No positive RU-KYA regional non-working public-holiday fact is therefore recorded for 2026. Coverage is limited to 2026 and must be re-reviewed for later periods.'
);

-- Intentionally no INSERT into regional_statutory_holiday_date_facts:
-- reviewed RU-KYA regional statutory public-holiday positives for 2026 = 0.
--
-- Any future correction must be explicit and source-reviewed; this migration
-- does not infer from ProductionCalendar, LOCAL_OVERRIDE, weekends or roster.

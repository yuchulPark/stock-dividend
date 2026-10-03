-- Run once against the existing public schema before starting the updated server.
-- Re-running is safe. No row or table is deleted. Stop if legacy KIS/OpenDART rows exist.
BEGIN;
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM public.dividend WHERE source IN ('KIS', 'OPEN_DART') AND ex_dividend_date IS NOT NULL) THEN
    RAISE EXCEPTION 'Legacy KIS/OpenDART event rows require a reviewed data migration';
  END IF;
END $$;
ALTER TABLE public.dividend ALTER COLUMN ex_dividend_date DROP NOT NULL;
ALTER TABLE public.dividend ADD COLUMN IF NOT EXISTS business_year integer;
ALTER TABLE public.dividend ADD COLUMN IF NOT EXISTS report_code varchar(5);
ALTER TABLE public.dividend ADD COLUMN IF NOT EXISTS filing_number varchar(14);
ALTER TABLE public.dividend ADD COLUMN IF NOT EXISTS share_class varchar(40);
ALTER TABLE public.dividend DROP CONSTRAINT IF EXISTS dividend_source_check;
ALTER TABLE public.dividend ADD CONSTRAINT dividend_source_check CHECK (source IN ('ALPHA_VANTAGE', 'KRX', 'OPEN_DART'));
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.dividend'::regclass AND conname = 'uk_dividend_asset_source_report') THEN
    ALTER TABLE public.dividend ADD CONSTRAINT uk_dividend_asset_source_report UNIQUE (stock_asset_id, source, business_year, report_code, share_class);
  END IF;
END $$;
ALTER TABLE public.dividend DROP CONSTRAINT IF EXISTS ck_dividend_event_or_report;
ALTER TABLE public.dividend ADD CONSTRAINT ck_dividend_event_or_report CHECK (
  (source <> 'OPEN_DART' AND ex_dividend_date IS NOT NULL AND business_year IS NULL AND report_code IS NULL AND filing_number IS NULL AND share_class IS NULL)
  OR
  (source = 'OPEN_DART' AND ex_dividend_date IS NULL AND record_date IS NULL AND payment_date IS NULL
   AND business_year IS NOT NULL AND business_year >= 2015
   AND report_code IS NOT NULL AND report_code IN ('11011','11012','11013','11014')
   AND filing_number IS NOT NULL AND share_class IS NOT NULL)
);
COMMIT;

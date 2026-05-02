-- ── v3: 打卡审核字段 ──────────────────────────────────────────
ALTER TABLE checkin_record
  ADD COLUMN IF NOT EXISTS review_status  SMALLINT NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS review_remark  VARCHAR(200);

-- review_status: 0=待审核, 1=通过(默认), 2=驳回
COMMENT ON COLUMN checkin_record.review_status IS '审核状态 0待审核 1通过 2驳回';

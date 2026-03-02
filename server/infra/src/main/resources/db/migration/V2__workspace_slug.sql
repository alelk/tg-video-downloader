-- Add slug column to workspaces table
-- slug: уникальный человекочитаемый идентификатор workspace (используется в URL и конфигурации)
ALTER TABLE workspaces ADD COLUMN slug TEXT NOT NULL DEFAULT '' CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$');

-- Backfill existing rows (если будут — генерируем slug из id)
UPDATE workspaces SET slug = 'workspace-' || SUBSTRING(id::text, 1, 8) WHERE slug = '';

-- Remove default after backfill — slug обязателен при вставке
ALTER TABLE workspaces ALTER COLUMN slug DROP DEFAULT;

-- Unique index
CREATE UNIQUE INDEX idx_workspaces_slug ON workspaces(slug);


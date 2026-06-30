#!/usr/bin/env node
/** Migrate simple sys widgets: strip CSS, rename classes to aipp-* */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = path.resolve(path.dirname(fileURLToPath(import.meta.url)),
  '../../ones/world-one/src/main/resources/static/widgets/system');

function stripWidgetCss(src) {
  return src
    .replace(/const STYLE_ID = '[^']+';\n\n?/g, '')
    .replace(/const CSS = `\n[\s\S]*?`;\n\n?/g, '')
    .replace(/function ensureStyles\(\) \{[\s\S]*?\}\n\n?/g, '')
    .replace(/  ensureStyles\(\);\n/g, '');
}

const files = {
  'alert.js': [
    ['sya-root', 'aipp-root'],
    ['sya-title', 'aipp-title'],
    ['sya-message', 'aipp-message'],
    ['sya-btn', 'aipp-btn aipp-btn--primary'],
    ['class="aipp-btn aipp-btn--primary"', 'class="aipp-btn aipp-btn--primary" style="align-self:flex-end"'],
  ],
  'prompt.js': [
    ['syp-root', 'aipp-root'],
    ['syp-title', 'aipp-title'],
    ['syp-message', 'aipp-message'],
    ['syp-input', 'aipp-input'],
    ['syp-btn-row', 'aipp-btn-row aipp-btn-row--end'],
    ['syp-btn secondary', 'aipp-btn aipp-btn--secondary'],
    ['syp-btn primary', 'aipp-btn aipp-btn--primary'],
  ],
  'progress.js': [
    ['spr-root', 'aipp-root'],
    ['spr-status', 'aipp-row'],
    ['spr-spinner', 'aipp-spinner'],
    ['spr-tool', 'aipp-callout'],
  ],
  'selection.js': [
    ['sys-sel-list', 'aipp-list aipp-list--boxed'],
    ['sys-sel-preview-shell', 'aipp-preview-shell'],
    ['sys-sel-hint', 'aipp-hint-line'],
    ['sys-sel-row', 'aipp-list-item aipp-list-item--flat'],
    ['sys-sel-open', 'aipp-list-item__open'],
    ['sys-sel-icon', 'aipp-icon-box'],
    ['sys-sel-info', 'aipp-item-info'],
    ['sys-sel-label', 'aipp-item-name'],
    ['sys-sel-sub', 'aipp-item-desc'],
    ['sys-sel-chevron', 'aipp-chevron'],
    ['sys-sel-done', 'aipp-done'],
    ["style=\"color:var(--danger)\"", 'class="aipp-error"'],
  ],
  'app-info.js': [
    ['sys-app-info-card', 'aipp-root aipp-panel'],
    ['sys-app-info-head', 'aipp-head'],
    ['sys-app-info-icon', 'aipp-icon-box aipp-icon-box--lg'],
    ['sys-app-info-titles', 'aipp-head-titles'],
    ['sys-app-info-name', 'aipp-name'],
    ['sys-app-info-desc', 'aipp-desc'],
    ['sys-app-info-meta', 'aipp-meta-grid'],
    ['sys-app-info-actions', 'aipp-actions'],
    ['sys-app-info-btn', 'aipp-btn aipp-btn--ghost'],
  ],
  'approval.js': [
    ['sap-root', 'aipp-root'],
    ['sap-title', 'aipp-title aipp-title--truncate'],
    ['sap-detail', 'aipp-message'],
    ['sap-context', 'aipp-context'],
    ['sap-ctx-row', 'aipp-kv-row'],
    ['sap-ctx-key', 'aipp-kv-key'],
    ['sap-conf low', 'aipp-conf aipp-conf--low'],
    ['sap-conf ok', 'aipp-conf aipp-conf--ok'],
    ['sap-changes', 'aipp-scroll-panel'],
    ['sap-change', 'aipp-change-row'],
    ['sap-section', 'aipp-col'],
    ['sap-section-title', 'aipp-section-title'],
    ['sap-row', 'aipp-field'],
    ['sap-label', 'aipp-label'],
    ['sap-input sap-error-frame', 'aipp-input aipp-input--error'],
    ['sap-input', 'aipp-input'],
    ['sap-btn-row', 'aipp-btn-row'],
    ['sap-btn approve', 'aipp-btn aipp-btn--success'],
    ['sap-btn reject', 'aipp-btn aipp-btn--danger'],
    ['sap-error', 'aipp-error'],
    ['sap-ok', 'aipp-status--ok'],
    ['sap-conf ${low', 'aipp-conf ${low'], // noop guard
  ],
  'plan.js': [
    ['spl-root', 'aipp-root'],
    ['spl-title', 'aipp-title'],
    ['spl-message', 'aipp-message'],
    ['spl-steps', 'aipp-col'],
    ['spl-step-title', 'aipp-section-title'],
    ['spl-step-desc', 'aipp-muted'],
    ['spl-step', 'aipp-section'],
    ['spl-opt', 'aipp-opt'],
    ['spl-btn-row', 'aipp-btn-row'],
    ['spl-btn secondary', 'aipp-btn aipp-btn--secondary'],
    ['spl-btn primary', 'aipp-btn aipp-btn--primary'],
    ['spl-done', 'aipp-done'],
    ['spl-superseded', 'aipp-superseded'],
    ["class=\"spl-btn ${", 'class="aipp-btn aipp-btn--'],
    ["opt.tool ? 'primary' : 'secondary'}", "opt.tool ? 'primary' : 'secondary'}"],
  ],
  'confirms.js': [
    ['scm-root', 'aipp-root'],
    ['scm-title', 'aipp-title'],
    ['scm-section-title', 'aipp-section-title'],
    ['scm-section-msg', 'aipp-message'],
    ['scm-section', 'aipp-section'],
    ['scm-opt', 'aipp-opt'],
    ['scm-files', 'aipp-files'],
    ['scm-file', 'aipp-row'],
    ['scm-manual', 'aipp-textarea'],
    ['scm-btn-row', 'aipp-btn-row'],
    ['scm-btn secondary', 'aipp-btn aipp-btn--secondary'],
    ['scm-btn primary', 'aipp-btn aipp-btn--primary'],
    ['scm-done', 'aipp-done'],
    ['scm-hint', 'aipp-hint--warning'],
    ['scm-sections', 'aipp-col'],
  ],
  'app-list.js': [
    ['sys-app-list', 'aipp-list aipp-list--boxed'],
    ['sys-app-preview-shell', 'aipp-preview-shell'],
    ['sys-app-row', 'aipp-list-item aipp-list-item--flat'],
    ['sys-app-open', 'aipp-list-item__open'],
    ['sys-app-icon', 'aipp-icon-box'],
    ['sys-app-info', 'aipp-item-info'],
    ['sys-app-name', 'aipp-item-name'],
    ['sys-app-desc', 'aipp-item-desc'],
    ['sys-app-empty', 'aipp-empty'],
    ['sys-app-warn', 'aipp-warn'],
    ['sys-app-config-btn', 'aipp-config-btn'],
  ],
};

for (const [file, reps] of Object.entries(files)) {
  const p = path.join(dir, file);
  let src = fs.readFileSync(p, 'utf8');
  src = stripWidgetCss(src);
  for (const [from, to] of reps) {
    src = src.split(from).join(to);
  }
  // plan.js fix botched template - repair spl-btn pattern
  src = src.replace(/class="aipp-btn aipp-btn--\$\{opt\.tool \? 'primary' : 'secondary'\}"/g,
    'class="aipp-btn ${opt.tool ? \'aipp-btn--primary\' : \'aipp-btn--secondary\'}"');
  fs.writeFileSync(p, src);
  console.log('Migrated', file);
}

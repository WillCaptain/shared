/**
 * Tool manifest placement and widget refresh semantics (protocol v2.7).
 *
 * <p><b>Coding agents:</b> read {@code spec/field-semantics.md} in this module before editing
 * {@code visibility}, {@code owner_widget}, {@code router_shortcut}, {@code mutates_display},
 * or widget {@code refresh_tool}. Those fields answer <em>different</em> questions — placement
 * (who/when), display side effects (stale UI), and reload contract (which tool to call).
 *
 * <p>Canonical helper: {@link org.twelve.aipp.tools.ToolPlacement}.
 *
 * @see org.twelve.aipp.tools.ToolPlacement
 */
package org.twelve.aipp.tools;

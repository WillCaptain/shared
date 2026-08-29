# Shared browser JavaScript

Browser-distributed JavaScript shared by Ones applications. The current package is the Outline
editor client (`outline-lang.js`), which calls the Java `outline-editor` backend.

See `README-outline-lang.md` for its API and integration contract. New unrelated browser packages
should use a capability-specific directory rather than accumulating in this folder.

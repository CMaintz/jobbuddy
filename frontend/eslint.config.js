// @ts-check
const eslint = require("@eslint/js");
const { defineConfig } = require("eslint/config");
const tseslint = require("typescript-eslint");
const angular = require("angular-eslint");
// Foundry base (line-length gate) — vendored; see eslint.config.foundry.cjs.
const foundry = require("./eslint.config.foundry.cjs");

module.exports = defineConfig([
  {
    // Legacy screens kept for reference until the rework merges — never linted.
    ignores: ["src/app/features/old/**"],
  },
  {
    files: ["**/*.ts"],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
      // Line-length gate — scoped to TS here (not HTML templates), where long lines
      // signal a statement that should be split or a value that wants a name.
      ...foundry,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      // "rb" is the resume-builder prefix (rbSection, rbResumePhoto).
      "@angular-eslint/directive-selector": [
        "error",
        {
          type: "attribute",
          prefix: ["app", "rb"],
          style: "camelCase",
        },
      ],
      // "jb" is the design-system prefix for shared components; "app" for feature screens.
      "@angular-eslint/component-selector": [
        "error",
        {
          type: "element",
          prefix: ["app", "jb"],
          style: "kebab-case",
        },
      ],
      // Leading underscore marks intentionally unused (e.g. signal reads for effect tracking).
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
      ],
      // Empty arrow functions are deliberate no-op subscribe handlers.
      "@typescript-eslint/no-empty-function": [
        "error",
        { allow: ["arrowFunctions"] },
      ],
    },
  },
  {
    files: ["**/*.html"],
    extends: [
      angular.configs.templateRecommended,
      angular.configs.templateAccessibility,
    ],
    rules: {
      // `x != null` deliberately covers undefined too.
      "@angular-eslint/template/eqeqeq": ["error", { allowNullOrUndefined: true }],
      // Real accessibility debt, tracked as warnings until a dedicated a11y pass.
      "@angular-eslint/template/label-has-associated-control": "warn",
      "@angular-eslint/template/click-events-have-key-events": "warn",
      "@angular-eslint/template/interactive-supports-focus": "warn",
    },
  }
]);

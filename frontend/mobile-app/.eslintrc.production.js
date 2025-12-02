module.exports = {
  extends: './.eslintrc.js',
  rules: {
    // Prevent console statements in production
    'no-console': ['error', {
      allow: [], // No console.* allowed in production
    }],

    // Prevent debugger statements
    'no-debugger': 'error',

    // Prevent alert/confirm/prompt
    'no-alert': 'error',

    // Require Logger import for logging
    'no-restricted-syntax': [
      'error',
      {
        selector: 'CallExpression[callee.object.name="console"]',
        message: 'Use Logger service instead of console.*',
      },
    ],

    // Prevent direct imports of console
    'no-restricted-globals': [
      'error',
      {
        name: 'console',
        message: 'Use Logger service from @/utils/Logger',
      },
    ],
  },
};

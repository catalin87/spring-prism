// @ts-check

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

/**
 * Creating a sidebar enables you to:
 - create an ordered group of docs
 - render a sidebar for each doc of that group
 - provide next/previous navigation

 The sidebars can be generated from the filesystem, or explicitly defined here.

 Create as many sidebars as you want.

 @type {import('@docusaurus/plugin-content-docs').SidebarsConfig}
 */
const sidebars = {
  docsSidebar: [
    'intro',
    'architecture',
    'configuration',
    'distributed-deployments',
    'dashboard',
    'grafana',
    'demo-app',
    'detectors',
    {
      type: 'category',
      label: 'MCP Integration',
      items: [
        'mcp',
        'mcp-tooling',
        'mcp-vscode',
        'mcp-jetbrains',
        'mcp-copilot',
        'mcp-docker-hosted',
      ],
    },
    'compatibility',
    'performance',
    'troubleshooting',
    'migration-guide',
    'release-readiness',
  ],
};

export default sidebars;

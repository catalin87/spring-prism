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
    {
      type: 'category',
      label: 'Start Here',
      items: ['intro', 'quickstart', 'configuration', 'production-playbook', 'architecture'],
    },
    {
      type: 'category',
      label: 'Operate',
      items: [
        'distributed-deployments',
        'dashboard',
        'grafana',
        'performance',
        'troubleshooting',
      ],
    },
    {
      type: 'category',
      label: 'Features & Examples',
      items: ['nlp-extensions', 'nlp-model-guide', 'demo-app', 'detectors'],
    },
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
    {
      type: 'category',
      label: 'Release & Quality',
      items: ['compatibility', 'integration-test-tracker', 'release-readiness', 'migration-guide'],
    },
  ],
};

export default sidebars;

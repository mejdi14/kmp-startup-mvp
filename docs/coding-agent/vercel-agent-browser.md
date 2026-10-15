# Vercel Agent Browser

[Vercel's agent-browser](https://github.com/vercel/agent-browser) is a CLI tool that lets Claude Code control a headless Chromium browser. This is useful for visually verifying frontend changes in a feedback loop — Claude makes a code change, screenshots the result, evaluates it, and iterates until it looks right.

## Prerequisites

- **Node.js** (v18+)
- **Claude Code** installed and configured
- A browser skill file at `.claude/browser/SKILL.md` (see [Browser Skill Reference](#browser-skill-reference))

## Installation

agent-browser is installed on first use via `npx`, but you need to install the Chromium binary once:

```bash
npx agent-browser install
```

This downloads a Chromium build managed by Playwright. It only needs to run once per machine.

## Running the Feedback Loop

The feedback loop works like this:

1. Start your dev server
2. Open the page in agent-browser
3. Ask Claude to build/modify a feature
4. Claude edits code, screenshots the result, evaluates, and iterates

### Step 1: Start the Dev Server

Make sure nothing else is using port 3000, then start Next.js:

```bash
# Check if port 3000 is in use
lsof -ti:3000

# Kill it if needed
kill $(lsof -ti:3000)

# Start the dev server
cd nextjs
pnpm dev
```

### Step 2: Open the Page in agent-browser

```bash
npx agent-browser open http://localhost:3000
```

### Step 3: Ask Claude to Build with Visual Feedback

Now you can ask Claude Code to build or modify a feature. Claude will:

1. **Edit the code** (components, styles, etc.)
2. **Screenshot the page** to see the rendered result
3. **Evaluate** whether it matches the goal
4. **Iterate** — adjust code and re-screenshot until satisfied

Example prompts that work well with the feedback loop:

```
"Build a testimonials section for the landing page. Use the browser to check
how it looks and iterate until the spacing and typography match the rest of
the page."

"The pricing cards look off on mobile. Use the browser at 375px width to
check and fix the layout."

"Add a dark mode toggle animation. Screenshot both light and dark mode to
verify it works."
```

### Useful Commands During the Loop

```bash
# Full page screenshot
npx agent-browser screenshot --full /tmp/screenshot.png

# Screenshot with element annotations (numbered labels)
npx agent-browser screenshot --annotate /tmp/annotated.png

# Test responsive layouts
npx agent-browser set viewport 375 812    # Mobile
npx agent-browser set viewport 768 1024   # Tablet
npx agent-browser set viewport 1440 900   # Desktop

# Test dark mode
npx agent-browser set media dark
npx agent-browser set media light

# Get accessibility tree (useful for checking structure)
npx agent-browser snapshot

# Navigate to a specific section
npx agent-browser open http://localhost:3000/#pricing
```

## Browser Skill Reference

The full command reference for agent-browser is defined in the browser skill file:

```
.claude/browser/SKILL.md
```

This file is loaded by Claude Code automatically when the browser agent is invoked. It contains all available commands (navigation, interaction, screenshots, network interception, etc.) along with project-specific defaults like login flows and key pages to test.

## Tips

- **Start with a screenshot** before making changes so Claude has a baseline to compare against.
- **Use `--full` screenshots** for landing pages to capture everything below the fold.
- **Set the viewport** before screenshotting to test specific breakpoints rather than the default size.
- **Use `snapshot`** (accessibility tree) when you need Claude to understand the DOM structure, not just the visual appearance.
- **Close the browser** when done (`npx agent-browser close`) to free up resources.
- **Dark mode testing** — use `set media dark` / `set media light` to toggle without clicking the UI toggle.

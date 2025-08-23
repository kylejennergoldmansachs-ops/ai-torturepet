import express from "express";
import bodyParser from "body-parser";
import fetch from "node-fetch";

const app = express();
app.use(bodyParser.json({ limit: "1mb" }));

const PORT = process.env.PORT || 3000;
const MISTRAL_API_KEY = process.env.MISTRAL_API_KEY;
if (!MISTRAL_API_KEY) {
  console.error("ERROR: set MISTRAL_API_KEY environment variable before starting.");
  process.exit(1);
}

const DEFAULT_PIXTRAL_AGENT = process.env.PIXTRAL_AGENT_ID || "ag:ddacd900:20250823:untitled-agent:633c61ee";
const DEFAULT_SUMMARIZER_AGENT = process.env.MAGISTRAL_AGENT_ID || "ag:ddacd900:20250823:untitled-agent:50c34ed9";

async function callAgentInvoke(agentId, inputText, extra = {}) {
  const url = `https://api.mistral.ai/v1/agents/${encodeURIComponent(agentId)}/invoke`;
  const payload = { input: inputText, ...extra };
  const resp = await fetch(url, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${MISTRAL_API_KEY}`,
      "Content-Type": "application/json",
      "Accept": "application/json"
    },
    body: JSON.stringify(payload)
  });
  if (!resp.ok) {
    const t = await resp.text();
    throw new Error(`Mistral agent invoke error: ${resp.status} ${t}`);
  }
  const data = await resp.json();
  return data;
}

async function callChatCompletion(model, messages, options = {}) {
  const url = `https://api.mistral.ai/v1/chat/completions`;
  const payload = { model, messages, ...options };
  const resp = await fetch(url, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${MISTRAL_API_KEY}`,
      "Content-Type": "application/json",
      "Accept": "application/json"
    },
    body: JSON.stringify(payload)
  });
  if (!resp.ok) {
    const t = await resp.text();
    throw new Error(`Mistral chat completion error: ${resp.status} ${t}`);
  }
  return resp.json();
}

app.post("/translate", async (req, res) => {
  try {
    const { user_text, sensory_snapshot, short_brain_summary } = req.body;
    const translatorPrompt = `
You are a TRANSLATOR agent. Input: a user's text message and a short sensory / brain summary.
Return ONLY well-formed JSON with the following fields:
- neural_inputs: an array of { kind: "text_embedding"|"sensory_stim", tokens: [ints]?, strength: float }
- neurogenesis: an array of requested new clusters { label: string, cluster_size: int, seed_embedding: [floats]? }
- memory_flags: an array of { summary: string, importance: float }
No natural language outside the JSON. If you cannot produce all fields, return empty arrays for them.

USER_TEXT:
${user_text}

SENSORY_SNAPSHOT:
${JSON.stringify(sensory_snapshot || {})}

SHORT_BRAIN_SUMMARY:
${JSON.stringify(short_brain_summary || {})}
`;

    let agentResponse;
    try {
      agentResponse = await callAgentInvoke(DEFAULT_PIXTRAL_AGENT, translatorPrompt, { role: "translator" });
      const maybeText = agentResponse?.output?.[0]?.content || agentResponse?.output?.content || JSON.stringify(agentResponse);
      const jsonStart = maybeText.indexOf("{");
      const jsonStr = jsonStart >= 0 ? maybeText.slice(jsonStart) : maybeText;
      let parsed;
      try {
        parsed = JSON.parse(jsonStr);
      } catch (e) {
        parsed = null;
      }
      if (parsed) {
        return res.json(parsed);
      }
    } catch (err) {
      console.warn("Agent invoke failed or returned unparsable JSON — falling back to chat:", err.message);
    }

    const model = process.env.FALLBACK_MODEL || "mistral-medium-1";
    const chatMessages = [
      { role: "system", content: "You are a TRANSLATOR. Return ONLY JSON as specified. No commentary." },
      { role: "user", content: translatorPrompt }
    ];
    const chatResp = await callChatCompletion(model, chatMessages, { max_tokens: 300, temperature: 0.0 });
    const assistantText = chatResp?.choices?.[0]?.message?.content || "{}";
    let parsed = null;
    try {
      parsed = JSON.parse(assistantText);
    } catch (e) {
      const s = assistantText;
      const i = s.indexOf("{");
      if (i >= 0) {
        try { parsed = JSON.parse(s.slice(i)); } catch (_) { parsed = null; }
      }
    }
    if (!parsed) parsed = { neural_inputs: [], neurogenesis: [], memory_flags: [] };
    return res.json(parsed);

  } catch (err) {
    console.error("Translate error:", err);
    res.status(500).json({ error: err.toString() });
  }
});

app.post("/cognitive", async (req, res) => {
  try {
    const { post_brain_summary, recent_user_text } = req.body;
    const cognitivePrompt = `
You are the COGNITIVE agent (higher mind). Input: a short brain summary and the user's latest message.
You MUST produce JSON with:
- user_text: text to send to the user (string)
- behavior_directives: object e.g. { motor_intent: [floats], verbal_tone: "calm" }
- archive: array of { summary: string, importance: float }

Take into account: post_brain_summary = ${JSON.stringify(post_brain_summary || {})}
Recent_user_text = "${(recent_user_text||"").replace(/"/g, '\"')}"

Return ONLY JSON, nothing else.
`;

    try {
      const agentResp = await callAgentInvoke(DEFAULT_PIXTRAL_AGENT, cognitivePrompt, { role: "cognitive" });
      const maybeText = agentResp?.output?.[0]?.content || agentResp?.output?.content || JSON.stringify(agentResp);
      const jsonStart = maybeText.indexOf("{");
      const jsonStr = jsonStart >= 0 ? maybeText.slice(jsonStart) : maybeText;
      try {
        const parsed = JSON.parse(jsonStr);
        return res.json(parsed);
      } catch (e) {
        console.warn("Agent returned unparsable JSON. Falling back to chat completion.");
      }
    } catch (err) {
      console.warn("Agent invoke failed:", err.message);
    }

    const model = process.env.FALLBACK_MODEL || "pixtral-large-1";
    const messages = [
      { role: "system", content: "You are a COGNITIVE agent. Return ONLY JSON as specified." },
      { role: "user", content: cognitivePrompt }
    ];
    const chatResp = await callChatCompletion(model, messages, { max_tokens: 600, temperature: 0.7 });
    const assistantText = chatResp?.choices?.[0]?.message?.content || "{}";
    let parsed = null;
    try {
      parsed = JSON.parse(assistantText);
    } catch (e) {
      const s = assistantText;
      const i = s.indexOf("{");
      if (i >= 0) {
        try { parsed = JSON.parse(s.slice(i)); } catch (_) { parsed = null; }
      }
    }
    if (!parsed) parsed = { user_text: "(error) couldn't generate response", behavior_directives: {}, archive: [] };
    return res.json(parsed);

  } catch (err) {
    console.error("Cognitive error:", err);
    res.status(500).json({ error: err.toString() });
  }
});

app.listen(PORT, () => {
  console.log(`Agent manager listening on port ${PORT}`);
  console.log("Using PIXTRAL_AGENT_ID =", DEFAULT_PIXTRAL_AGENT);
});

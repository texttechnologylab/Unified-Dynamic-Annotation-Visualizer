<#macro chat title>
  <div class="dv-chat-bot collapsed">
    <div class="dv-chat-header">
      <div class="dv-chat-title">
        <i class="bi bi-robot"></i>
        <span>${title}</span>
      </div>
      <div class="dv-chat-toolbar">
        <button id="btn-toggle" class="dv-btn" title="Expand/Contract">
          <i class="bi bi-arrows-angle-expand"></i>
          <i class="bi bi-arrows-angle-contract"></i>
        </button>
        <button id="btn-collapse" class="dv-btn" title="Collapse">
          <i class="bi bi-chevron-down"></i>
        </button>
      </div>
    </div>

    <div class="dv-chat">
      <div class="dv-chat-messages"></div>
      <div class="dv-chat-input" tabindex="0">
        <textarea rows="1" placeholder="Ask a question..."></textarea>
      
        <div class="dv-chat-controls">
          <select id="model-select" class="dv-select" title="Select a model"></select>
          <select id="context-select" class="dv-select" title="Select a context"></select>
          <button class="dv-btn dv-chat-send" title="Send message" disabled>
            <i class="bi bi-send"></i>
            <span class="spinner-grow spinner-grow-sm dv-hidden"></span>
          </button>
        </div>
      </div>
    </div>
  </div>
</#macro>
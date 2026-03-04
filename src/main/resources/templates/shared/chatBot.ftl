<#macro chatBot widgets>
  <div class="dv-chat-bot collapsed">
    <div class="dv-chat-header">
      <div class="dv-chat-title">
        <i class="bi bi-robot"></i>
        <span>Cha<span class="dv-bold">r</span>tGPT</span>
      </div>
      <button class="dv-btn" title="Collapse">
        <i class="bi bi-chevron-down"></i>
      </button>
    </div>

    <div class="dv-chat">
      <div class="dv-chat-messages"></div>
      <div class="dv-chat-input" tabindex="0">
        <textarea rows="1" placeholder="Ask a question..."></textarea>
      
        <div class="dv-chat-controls">
          <select id="model-select" class="dv-select" title="Select a model"></select>
          <select id="context-select" class="dv-select" title="Select a context">
            <option value="">Context</option>
            <#list widgets as widget>
              <#if !widget.type?contains("Static")>
                <option value="${widget.id}">${widget.title}</option>
              </#if>
            </#list>
          </select>
          <button class="dv-btn dv-chat-send" title="Send message" disabled>
            <i class="bi bi-send"></i>
            <span class="spinner-grow spinner-grow-sm dv-hidden"></span>
          </button>
        </div>
      </div>
    </div>
  </div>
</#macro>
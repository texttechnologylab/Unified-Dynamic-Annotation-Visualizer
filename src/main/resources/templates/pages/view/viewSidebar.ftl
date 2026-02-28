<#include "/shared/accordion.ftl">
<#include "/shared/pipelineSwitcher.ftl">

<#macro sidebar id pipelines widgets>
  <aside class="dv-sidebar">
    <a class="dv-sidebar-header" href="/">
      <img src="/img/logo.png" alt="Logo">
      <span class="dv-bold">UDAV</span>
    </a>

    <div class="dv-sidebar-body">
      <a 
        class="dv-btn dv-menu-link"
        href="/"
      >
        <i class="bi bi-list"></i>
        <span>Menu</span>
      </a>
      <a 
        class="dv-btn dv-menu-link"
        href="/editor/${id}"
      >
        <i class="bi bi-pencil"></i>
        <span>Edit Pipeline</span>
      </a>

      <@accordion icon="bi bi-robot" title="ChaRtGPT">
        <div class="dv-chat-bot">
          <div class="dv-chat"></div>

          <div class="dv-chat-controls">
            <input class="dv-text-input" type="text" placeholder="Ask a question">
            <button class="dv-btn dv-chat-send" title="Send message" disabled>
              <i class="bi bi-send"></i>
              <span class="spinner-grow spinner-grow-sm dv-hidden"></span>
            </button>
          </div>
          <div class="dv-chat-controls">
            <select id="model-select" class="dv-select" title="Select a model"></select>
            <select class="dv-select" title="Select a context">
              <#list widgets as widget>
                <#if !widget.type?contains("Static")>
                  <option value="${widget.id}">${widget.title}</option>
                </#if>
              </#list>
            </select>
          </div>
        </div>
      </@accordion>
      
      <div class="dv-menu-title">Pipeline</div>
      <@pipelineSwitcher pipelines=pipelines selected=id />
      
      <div class="dv-menu-title">Corpus Filter</div>
      <div class="dv-corpus-filter">
        <@accordion icon="bi bi-file-earmark-text" title="Files">
          <div id="file-filter">
            <div class="dv-dropdown-container">
              <input
                class="dv-autocomplete-input"
                type="text"
                placeholder="Type to search"
              />
              <div class="dv-dropdown"></div>
            </div>
            <div class="m-1"> 
              Remove or add checkboxes via the search to customize the filter.
            </div>

            <div class="dv-filter-checkboxes"></div>
            <div class="dv-divider"></div>
            <span class="dv-info-text"></span>
          </div>
        </@accordion>

        <@accordion icon="bi bi-tags" title="Tags">
          <div id="tag-filter">
            <div class="dv-dropdown-container">
              <input
                class="dv-autocomplete-input"
                type="text"
                placeholder="Type to search"
              />
              <div class="dv-dropdown"></div>
            </div>

            <div class="m-1"> 
              Remove or add checkboxes via the search to customize the filter.
            </div>

            <div class="dv-filter-checkboxes"></div>
            <div class="dv-divider"></div>
            <span class="dv-info-text"></span>
          </div>
        </@accordion>

        <@accordion icon="bi bi-calendar-week" title="Date">
          <div id="date-filter" class="dv-filter-date-inputs">
            <div>
              <span>Start</span>
              <input type="date" class="dv-date-input" />
            </div>
            <div>
              <span>End</span>
              <input type="date" class="dv-date-input" />
            </div>
          </div>
        </@accordion>

        <div class="dv-filter-buttons mt-2">
          <button
            id="apply-button"
            class="dv-btn-primary"
            type="button"
          >
            <i class="bi bi-funnel"></i>
            <span>Apply</span>
          </button>
          <button
            id="reset-button"
            class="dv-btn-outline dv-hidden"
            type="button"
          >
            <i class="bi bi-arrow-counterclockwise"></i>
            <span>Reset</span>
          </button>
        </div>
      </div>
    </div>
  </aside>
</#macro>

<template id="result-template">
  <button class="dv-btn dv-autocomplete-result" type="button">
    <span></span>
    <i class="bi bi-plus-lg"></i>
  </button>
</template>

<template id="checkbox-template">
  <label class="dv-filter-checkbox">
    <div class="dv-checkbox-container">
      <input
        class="dv-check-input form-check-input"
        type="checkbox"
        checked
      />
      <span class="dv-text-truncate"></span>
    </div>
    <button class="dv-btn-delete" type="button">
      <i class="bi bi-x-lg"></i>
    </button>
  </label>
</template>
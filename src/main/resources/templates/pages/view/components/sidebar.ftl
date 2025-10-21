<#include "/shared/accordion.ftl">
<#include "/pages/view/components/pipelineSwitcher.ftl">

<#macro sidebar id pipelines>
  <aside class="dv-sidebar">
    <div class="dv-sidebar-header">
      <h1 class="dv-bold">Dynamic Visualizations</h1>
    </div>

    <div class="dv-sidebar-body">
      <a 
        class="dv-btn dv-menu-link"
        href="/"
      >
        <i class="bi bi-list"></i>
        <span>Menu</span>
      </a>
      
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

        <div class="dv-centered mt-2">
          <button
            id="apply-button"
            class="dv-btn-outline"
            type="button"
          >
            Apply filter
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
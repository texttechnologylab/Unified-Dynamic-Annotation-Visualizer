<#macro pipelineSwitcher pipelines selected>
  <div class="dv-dropdown-container">
    <a class="dv-pipeline-switcher-trigger">
      <div class="dv-pipeline-switcher-title">
        <i class="bi bi-clipboard-data"></i>
        <span class="dv-text-truncate">${selected}</span>
      </div>
      <i class="bi bi-chevron-expand"></i>
    </a>
    <div class="dv-dropdown">
      <#list pipelines as id>
        <#if id != selected>
          <a
            class="dv-pipeline-switcher-item"
            title="${id}"
            href="/view/${id}"
          >
            <div class="dv-pipeline-switcher-title">
              <i class="bi bi-clipboard-data"></i>
              <span class="dv-text-truncate">${id}</span>
            </div>
          </a>
        </#if>
      </#list>
    </div>
  </div>
</#macro>

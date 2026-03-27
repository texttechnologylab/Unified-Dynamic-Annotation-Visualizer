<#macro pipelineSwitcher pipelines id name>
  <div class="dv-dropdown-container">
    <a class="dv-pipeline-switcher-trigger">
      <div class="dv-pipeline-switcher-title">
        <i class="bi bi-clipboard-data"></i>
        <span class="dv-text-truncate">${name}</span>
      </div>
      <i class="bi bi-chevron-expand"></i>
    </a>
    <div class="dv-dropdown">
      <#list pipelines as pipeline>
        <#if pipeline != id>
          <a
            class="dv-pipeline-switcher-item"
            title="${pipeline}"
            href="/view/${pipeline}"
          >
            <div class="dv-pipeline-switcher-title">
              <i class="bi bi-clipboard-data"></i>
              <span class="dv-text-truncate">${pipeline}</span>
            </div>
          </a>
        </#if>
      </#list>
    </div>
  </div>
</#macro>

<#include "/shared/toolbar.ftl">
<#include "/shared/sidepanel.ftl">

<#macro grid widgets>
  <div class="grid-stack">
    <#list widgets as widget>
      <div class="grid-stack-item" gs-id="${widget.id}">

        <#if widget.type?contains("Static")>
          <div
            class="grid-stack-item-content hide"
            title="${widget.title}"
            data-dv-widget="${widget.id}"
          >
          </div>

        <#else>
          <div
            class="grid-stack-item-content dv-chart hide"
            data-dv-widget="${widget.id}"
          >
            <@toolbar id=widget.id title=widget.title />

            <div class="dv-chart-area">
              <@sidepanel id=widget.id title="Controls" />
            </div>
          </div>
        </#if>

      </div>
    </#list>
  </div>
</#macro>

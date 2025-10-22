<#include "/shared/toolbar.ftl">
<#include "/shared/sidepanel.ftl">

<#macro grid widgets>
  <div class="grid-stack">
    <#list widgets as widget>
      <div class="grid-stack-item" gs-id="${widget.id}">

        <#if widget.type == "StaticText">
          <div
            class="grid-stack-item-content hide"
            title="${widget.title}"
            data-dv-widget="${widget.id}"
          >
            <div class="${widget.options.style}">
              ${widget.text}
            </div>
          </div>
        <#elseif widget.type == "StaticImage">
          <div
            class="grid-stack-item-content hide"
            title="${widget.title}"
            data-dv-widget="${widget.id}"
          >
            <img
              src="${widget.src}"
              width="100%"
              height="100%"
            >
          </div>
        <#elseif widget.type == "StaticVideo">
          <div
            class="grid-stack-item-content overflow-hidden hide"
            title="${widget.title}"
            data-dv-widget="${widget.id}"
          >
            <video
              src="${widget.src}"
              width="100%"
              height="100%"
              <#if widget.options.controls>
                controls
              </#if>
              <#if widget.options.autoplay>
                autoplay
              </#if>
            ></video>
          </div>
        <#elseif widget.type == "StaticIFrame">
          <div
            class="grid-stack-item-content overflow-hidden hide"
            title="${widget.title}"
            data-dv-widget="${widget.id}"
          >
            <iframe
              src="${widget.src}"
              width="100%"
              height="100%"
            ></iframe>
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

<#macro grid>
  <div class="grid-stack"></div>

  <template id="chart-widget-template">
    <div class="grid-stack-item-content dv-chart">
      <div class="dv-toolbar">
        <button
          class="dv-btn dv-btn-toolbar"
          type="button"
          title="Controls"
          data-dv-toggle="sidepanel"
          data-dv-target="#"
        >
          <i class="bi bi-sliders"></i>
        </button>

        <span class="dv-toolbar-title" title=""></span>

        <button
          class="dv-btn dv-btn-toolbar"
          type="button"
          title="Exports"
          data-bs-toggle="dropdown"
        >
          <i class="bi bi-download"></i>
        </button>
        <div class="dropdown-menu">
          <div class="dv-dropdown-menu"></div>
        </div>
      </div>

      <div class="dv-chart-area">
        <div id="#" class="dv-sidepanel">
          <div class="dv-sidepanel-header">
            <span class="dv-title">Controls</span>
            <button class="dv-btn-close" data-dv-dismiss="sidepanel"></button>
          </div>
          <div class="dv-sidepanel-body"></div>
        </div>

      </div>
    </div>
  </template>

  <template id="static-widget-template">
    <div class="grid-stack-item-content"></div>
  </template>
</#macro>

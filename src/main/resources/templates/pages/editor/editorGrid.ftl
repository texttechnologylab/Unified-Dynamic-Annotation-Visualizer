<#macro grid>
  <div class="grid-stack"></div>

  <template id="chart-widget-template">
    <div class="grid-stack-item-content dv-chart">
      <div class="dv-toolbar">
        <button
          class="dv-btn dv-btn-toolbar"
          type="button"
          title="Edit"
        >
          <i class="bi bi-pencil"></i>
        </button>

        <span class="dv-toolbar-title"></span>

        <button
          class="dv-btn dv-btn-toolbar"
          type="button"
          title="Remove"
        >
          <i class="bi bi-x-lg"></i>
        </button>
      </div>

      <div class="dv-chart-area"></div>
    </div>
  </template>

  <template id="static-widget-template">
    <div class="grid-stack-item-content">
      <div class="dv-autohide-toolbar">
        <button
          class="dv-btn dv-btn-toolbar"
          type="button"
          title="Edit"
        >
          <i class="bi bi-pencil"></i>
        </button>
        <button
          class="dv-btn dv-btn-toolbar"
          type="button"
          title="Remove"
          >
          <i class="bi bi-x-lg"></i>
        </button>
      </div>
    </div>
  </template>
</#macro>

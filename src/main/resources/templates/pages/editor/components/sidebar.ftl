<#include "/shared/accordion.ftl">

<#macro sidebar id>
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

      <div class="dv-menu-title">Pipeline Editor</div>
      <label class="dv-label">
        <span>Identifier:</span>
        <input id="identifier-input" type="text" class="dv-text-input" value="${id}" />
      </label>

      <@accordion icon="bi bi-database" title="Sources">
        <p>Sources are the basic annotation types for the charts.</p>

      </@accordion>

      <@accordion icon="bi bi-archive" title="DerivedGenerators">
        <p>Generators are data representations for the charts. They keep record of all the data that is relevant for the final charts.</p>

      </@accordion>

      <@accordion icon="bi bi-grid" title="Widgets">
        <p>Add new widgets by dragging them into the grid area to the right.</p>
        <div class="dv-add-widgets-container">
          <template id="add-widget-template">
            <div class="dv-add-widget">
              <div class="dv-add-widget-draggable">
                <i></i>
              </div>
              <span class="dv-add-widget-title"></span>
            </div>
          </template>
        </div>
      </@accordion>
    </div>

    <div class="dv-sidebar-footer">
      <div class="dv-btn-group">
        <button id="cancel-button" type="button" class="dv-btn-outline">
          Cancel
        </button>
        <button id="save-button" type="button" class="dv-btn-primary">
          Save
        </button>
      </div>
    </div>
  </aside>
</#macro>

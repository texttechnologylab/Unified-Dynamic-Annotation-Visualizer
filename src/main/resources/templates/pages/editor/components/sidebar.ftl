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
      <label class="dv-label w-100">
        <span>Identifier:</span>
        <input id="identifier-input" type="text" class="dv-text-input" value="${id}" />
      </label>

      <@accordion icon="bi bi-database" title="Generators">
        <p>
          Generators are data representations for the charts. 
          They keep record of all the data that is relevant for the charts.
        </p>
        
        <div class="dv-title">Existing Generators:</div>
        <div id="added-generators" class="dv-generators-container">
          <template id="added-generator-template">
            <div class="dv-generator">
              <div class="dv-generator-header">
                <div class="dv-generator-title">
                  <span class="dv-generator-token"></span>
                  <span class="dv-generator-type"></span>
                </div>
                <div class="d-flex">
                  <button class="dv-btn" type="button" title="Edit">
                    <i class="bi bi-pencil"></i>
                  </button>
                  <button class="dv-btn" type="button" title="Remove">
                    <i class="bi bi-x-lg"></i>
                  </button>
                </div>
              </div>
              <div class="dv-generator-body"></div>
            </div>
          </template>
        </div>
        
        <div class="dv-title">Available Generators:</div>
        <div id="available-generators" class="dv-generators-container">
          <template id="available-generator-template">
            <div class="dv-generator">
              <div class="dv-generator-header">
                <div class="dv-generator-title">
                  <span class="dv-generator-token"></span>
                  <span class="dv-generator-type"></span>
                </div>
                <button class="dv-btn" type="button">
                  <i class="bi bi-plus-lg"></i>
                </button>
              </div>
            </div>
          </template>
        </div>
      </@accordion>

      <@accordion icon="bi bi-grid" title="Widgets">
        <p>
          Add new widgets by dragging them into the grid area to the right.<br>
          <strong>Note:</strong> Dynamic widgets in the editor will display sample data for preview purposes.
        </p>
        <div class="dv-available-widgets-container">
          <template id="available-widget-template">
            <div class="dv-available-widget">
              <div class="dv-available-widget-draggable">
                <i></i>
              </div>
              <span class="dv-available-widget-title"></span>
            </div>
          </template>
        </div>
      </@accordion>
    </div>

    <div class="dv-sidebar-footer">
      <div class="dv-btn-group">
        <button id="discard-button" type="button" class="dv-btn-outline">
          <i class="bi bi-x-lg"></i>
          <span>Discard</span>
        </button>
        <button id="save-button" type="button" class="dv-btn-primary">
          <i class="bi-floppy"></i>
          <span>Save</span>
        </button>
      </div>
    </div>
  </aside>
</#macro>

(function () {
  /**
   * Opens the Copilot window with the given query, assistant ID, and contextual data.
   * @param {string} q - The user query.
   * @param {string} assistantId - The Copilot assistant ID.
   * @param {Object} messageData - Additional data (context, title, selected records, etc).
   */
  function openCopilotWindow(q, assistantId, messageData) {
    // Encode the query if present
    q = q ? encodeURI(q) : '';

    // Determine the assistant ID
    if (!assistantId && messageData["assistantId"]) {
      assistantId = messageData["assistantId"];
    }
    if (assistantId) {
      const idRegex = /^[A-Fa-f0-9]{32}$/;
      assistantId = idRegex.test(assistantId) ? assistantId : '';
    } else {
      assistantId = '';
    }

    // Determine context title based on number of selected records
    let contextTitle = messageData.tabTitle;
    if (messageData.selectedRecords) {
      const selectedCount = messageData.selectedRecords.length;
      if (selectedCount === 1) {
        contextTitle = `${messageData.tabTitle} - ${messageData.selectedRecords[0]._identifier || messageData.tabTitle}`;
      } else if (selectedCount >= 2) {
        contextTitle = `${messageData.tabTitle} - ${selectedCount} selected`;
      }
    }
    messageData.contextTitle = contextTitle;

    // Prepare the URL for the iframe
    const iframeURL = `web/com.etendoerp.copilot.dist/?question=${q}&assistant_id=${assistantId}&context_title=${encodeURI(contextTitle)}`;

    // Dimensions and styles
    const LIGHT_GRAY_COLOR = "#F2F5F9";
    const WINDOW_WIDTH = 425;
    const WINDOW_HEIGHT = 650;
    const MAXIMIZED_WINDOW_HEIGHT = 650;
    const MAXIMIZED_WINDOW_WIDTH = 425;
    const MINIMIZED_WINDOW_HEIGHT = 55;
    const MINIMIZED_WINDOW_WIDTH = 131;
    const MARGIN_CONTAINER_HORIZONTAL = 1;
    const MARGIN_CONTAINER_VERTICAL = 6;
    const MARGIN_CONTAINER_FULL_SCREEN = 12;
    const MARGIN_CONTAINER_FULL_SCREEN_HORIZONTAL = 13;
    const MARGIN_CONTAINER_FULL_SCREEN_VERTICAL = 18;

    // Adjust window to full screen
    function adjustFullScreenWindowPosition() {
      const header = document.getElementById('chatHeader');
      if (header) header.style.backgroundColor = LIGHT_GRAY_COLOR;

      const reactIframe = document.getElementById('react-iframe');
      const reactDoc = reactIframe?.contentDocument || reactIframe?.contentWindow?.document;
      if (reactDoc) {
        const iframeSelector = reactDoc.getElementById('iframe-selector');
        const iframeContainer = reactDoc.getElementById('iframe-container');
        const assistantTitle = reactDoc.getElementById('assistant-title');

        if (assistantTitle) assistantTitle.style.display = 'flex';

        if (iframeSelector && iframeContainer) {
          iframeSelector.classList.add("iframe-selector-full-screen");
          iframeContainer.classList.add("iframe-container-full-screen");
        }
      }

      const imgElement = document.getElementById('maximizeIcon');
      if (imgElement) imgElement.src = "web/images/maximize-2.svg";

      window.copilotWindow.setLeft(MARGIN_CONTAINER_FULL_SCREEN);
      window.copilotWindow.setTop(MARGIN_CONTAINER_FULL_SCREEN);
      window.copilotWindow.setWidth(isc.Page.getWidth() - MARGIN_CONTAINER_FULL_SCREEN_HORIZONTAL);
      window.copilotWindow.setHeight(isc.Page.getHeight() - MARGIN_CONTAINER_FULL_SCREEN_VERTICAL);
    }

    // Adjust window to minimized state
    function adjustMinimizeWindowPosition() {
      const widget = document.querySelector('.widgetContainer');
      if (widget) widget.style.setProperty('border', '0px solid #666', 'important');

      const body = document.getElementById('chatBody');
      if (body) body.style.display = 'none';

      const button = document.getElementById('button-minimize');
      if (button) button.style.display = 'flex';

      window.copilotWindow.setHeight(MINIMIZED_WINDOW_HEIGHT);

      const newLeft = Math.max(0, isc.Page.getWidth() - MINIMIZED_WINDOW_WIDTH - MARGIN_CONTAINER_HORIZONTAL);
      const newTop = Math.max(0, isc.Page.getHeight() - MINIMIZED_WINDOW_HEIGHT - MARGIN_CONTAINER_VERTICAL);

      window.copilotWindow.setLeft(newLeft);
      window.copilotWindow.setTop(newTop);
      window.copilotWindow.setWidth(MINIMIZED_WINDOW_WIDTH);
      window.copilotWindow.setHeight(MINIMIZED_WINDOW_HEIGHT);
    }

    // Adjust window to a "maximized" (but not full screen) state
    function adjustMaximizeWindowPosition() {
      const header = document.getElementById('chatHeader');
      if (header) header.style.backgroundColor = '#FFFFFF';

      const widget = document.querySelector('.widgetContainer');
      if (widget) {
        widget.style.setProperty('margin', '0px', 'important');
        widget.style.setProperty('border', '1px solid #666', 'important');
      }

      const reactIframe = document.getElementById('react-iframe');
      const reactDoc = reactIframe?.contentDocument || reactIframe?.contentWindow?.document;
      if (reactDoc) {
        const iframeSelector = reactDoc.getElementById('iframe-selector');
        const iframeContainer = reactDoc.getElementById('iframe-container');
        const assistantTitle = reactDoc.getElementById('assistant-title');

        if (assistantTitle) assistantTitle.style.display = 'none';
        if (iframeSelector && iframeContainer) {
          iframeSelector.classList.remove("iframe-selector-full-screen");
          iframeContainer.classList.remove("iframe-container-full-screen");
        }
      }

      const body = document.getElementById('chatBody');
      if (body) body.style.display = 'flex';

      const imgElement = document.getElementById('maximizeIcon');
      if (imgElement) imgElement.src = "web/images/maximize.svg";

      const button = document.getElementById('button-minimize');
      if (button) button.style.display = 'none';

      window.copilotWindow.setWidth(MAXIMIZED_WINDOW_WIDTH);
      window.copilotWindow.setHeight(MAXIMIZED_WINDOW_HEIGHT);

      const newLeft = Math.max(0, isc.Page.getWidth() - MAXIMIZED_WINDOW_WIDTH - MARGIN_CONTAINER_HORIZONTAL);
      const newTop = Math.max(0, isc.Page.getHeight() - MAXIMIZED_WINDOW_HEIGHT - MARGIN_CONTAINER_VERTICAL);

      window.copilotWindow.setLeft(newLeft);
      window.copilotWindow.setTop(newTop);
    }

    // Handle window resizing event
    function resizeWindow() {
      if (!window.copilotWindow) return;

      if (window.copilotWindow.height === MAXIMIZED_WINDOW_HEIGHT) {
        adjustMaximizeWindowPosition();
      } else if (window.copilotWindow.height > MAXIMIZED_WINDOW_HEIGHT) {
        adjustFullScreenWindowPosition();
      } else {
        adjustMinimizeWindowPosition();
      }
    }

    // Expose window actions globally
    window.handleMinimize = adjustMinimizeWindowPosition;
    window.handleMaximize = adjustMaximizeWindowPosition;
    window.handleFullScreenWindow = function () {
      if (window.copilotWindow.height === MAXIMIZED_WINDOW_HEIGHT) {
        adjustFullScreenWindowPosition();
      } else {
        adjustMaximizeWindowPosition();
      }
    };
    window.closeCopilotWindow = function () {
      if (window.copilotWindow) {
        window.copilotWindow.destroy();
        window.copilotWindow = null;
      }
    };

    // Create or update Copilot window
    if (!window.copilotWindow) {
      window.copilotWindow = isc.Window.create({
        width: WINDOW_WIDTH,
        styleName: 'widgetContainer',
        height: WINDOW_HEIGHT,
        canDragReposition: true,
        headerProperties: { height: "0px" },
        backgroundColor: LIGHT_GRAY_COLOR,
        items: [
          isc.HTMLPane.create({
            width: "100%",
            height: "100%",
            contents: `
              <div style="width:100%; height:100%; display:flex; flex-direction:column;">
                <style>
                  .icon-button {
                    width: 24px;
                    height: 24px;
                    cursor: pointer;
                    margin-left: 8px;
                  }
                  .close-button-container {
                    padding: 0.3rem;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    border-radius: 0.5rem;
                    cursor: pointer;
                  }
                  .close-button-container:hover {
                    background-color: #EFF1F7;
                  }
                  .copilot-logotype {
                    width: 3rem;
                    height: 3rem;
                  }
                  .container-header {
                    display: flex;
                    justify-content: space-between;
                    padding: 0px 12px;
                    align-items: center;
                    background-color: #FFFFFF;
                    border-bottom: 1px solid #D3D6E1;
                    height: 56px;
                  }
                  .content-header {
                    display: flex;
                    gap: 0.75rem;
                    align-items: center;
                    padding-top: 0.5rem;
                    padding-bottom: 0.5rem;
                    justify-content: center;
                  }
                  .copilot-title {
                    font-size: 1.5rem;
                    font-weight: bold;
                    color: #666666;
                  }
                  .copilot-title-minimize {
                    font-size: 16px;
                    font-weight: bold;
                    color: #FFFFFF;
                  }
                  .normal {
                    display: flex;
                    flex-direction: column;
                  }
                  .action-buttons-container {
                    align-items: center;
                    display: flex;
                  }
                  .copilot-minimize-button {
                    display: none;
                    height: 100%;
                    width: 100%;
                    border-radius: 20px;
                    background-color: #202452;
                    justify-content: center;
                    align-items: center;
                    border-style: none;
                  }
                  .copilot-logotype-minimize {
                    width: 32px;
                    height: 32px;
                    margin-right: 8px;
                  }
                  #chatBody {
                    display: flex;
                    flex-direction: column;
                    height: 100%;
                  }
                </style>

                <button onclick="window.handleMaximize()" id="button-minimize" class="copilot-minimize-button">
                  <img id="copilote-img-min" class="copilot-logotype-minimize" src="web/images/copilot-min.png" alt="Logo Copilot">
                  <span class="copilot-title-minimize">Copilot</span>
                </button>

                <div id="chatBody">
                  <div id="chatHeader" class="container-header">
                    <div class="content-header">
                      <img id="copilote-img-max" class="copilot-logotype" src="web/images/copilot.png" alt="Logo Copilot">
                      <span class="copilot-title">Copilot</span>
                    </div>
                    <div class="action-buttons-container">
                      <img class="icon-button" onclick="window.handleMinimize()" src="web/images/minimize.svg" alt="Minimize">
                      <img id="maximizeIcon" class="icon-button" onclick="window.handleFullScreenWindow()" src="web/images/maximize.svg" alt="Maximize">
                      <img class="icon-button" onclick="window.closeCopilotWindow()" src="web/images/close.svg" alt="Close">
                    </div>
                  </div>

                  <iframe
                    id="react-iframe"
                    style="display: block; width: 100%; flex:1"
                    src="${iframeURL}"
                    title="Copilot Chat"
                    frameborder="0"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                    allowfullscreen>
                  </iframe>
                </div>
              </div>
            `
          })
        ]
      });

      // Initial setup for new window
      window.copilotWindow.show();
      isc.Page.setEvent("resize", resizeWindow);
      adjustMaximizeWindowPosition();

      // Attach load event listener to iframe for initial context
      const iframe = document.getElementById('react-iframe');
      if (iframe) {
        iframe.addEventListener('load', () => {
          sendContextMessage();
        });
      } else {
        setTimeout(sendContextMessage, 500);
      }
    } else {
      // If window already exists, just send the new context and ensure it’s visible
      sendContextMessage();
      if (!window.copilotWindow.isVisible() || window.copilotWindow.height === MINIMIZED_WINDOW_HEIGHT) {
        window.copilotWindow.show();
        adjustMaximizeWindowPosition();
      }
    }

    // Send context message to the iframe
    function sendContextMessage() {
      const iframe = document.getElementById('react-iframe');
      if (iframe && iframe.contentWindow) {
        iframe.contentWindow.postMessage({
          type: 'COPILOT_CONTEXT',
          data: messageData
        }, '*');
      } else {
        setTimeout(sendContextMessage, 500);
      }
    }
  }

  // Button properties to open/update the Copilot window
  const buttonProps = {
    action: function () {
      const view = this.view;
      const grid = view.viewGrid;
      const selectedRecords = grid.getSelectedRecords();
      const isFormEditing = !!view.isShowingForm;

      // Filter selectedRecords to include only string, number, or boolean values
      const filteredSelectedRecords = selectedRecords.map(record => {
        const filteredRecord = {};
        Object.keys(record).forEach(key => {
          const value = record[key];
          if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
            filteredRecord[key] = value;
          }
        });
        return filteredRecord;
      });

      // Prepare contextual data
      const activeWindowInfo = {
        windowId: view.windowId,
        tabId: view.tabId,
        tabTitle: view.tabTitle,
        selectedRecords: filteredSelectedRecords,
        isFormEditing: isFormEditing,
      };

      // If in edit mode, include real-time form values
      if (isFormEditing && view.viewForm && view.viewForm.values) {
        const formValues = {};
        Object.keys(view.viewForm.values).sort().forEach(key => {
          const value = view.viewForm.values[key];
          if (typeof value === 'string' || typeof value === 'number') {
            formValues[key] = value;
          }
        });
        activeWindowInfo.formValues = formValues;
      }

      // Open or update the Copilot window with the initial context
      openCopilotWindow(null, null, activeWindowInfo);

      // Listen for record selection changes
      if (typeof grid.addDataUpdatedHandler === 'function') {
        grid.addDataUpdatedHandler(function () {
          const newSelectedRecords = grid.getSelectedRecords();
          const newFilteredRecords = newSelectedRecords.map(record => {
            const filteredRecord = {};
            Object.keys(record).forEach(key => {
              const value = record[key];
              if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
                filteredRecord[key] = value;
              }
            });
            return filteredRecord;
          });

          const updatedWindowInfo = {
            windowId: view.windowId,
            tabId: view.tabId,
            tabTitle: view.tabTitle,
            selectedRecords: newFilteredRecords,
            isFormEditing: !!view.isShowingForm,
          };

          // Send updated context without recreating the window
          openCopilotWindow(null, null, updatedWindowInfo);
        });
      }
    },
    buttonType: 'etcop',
    prompt: 'Copilot',
    updateState: function () {
      // No state update required at the moment
    }
  };

  // Register the custom toolbar button
  OB.ToolbarRegistry.registerButton(
    buttonProps.buttonType,
    isc.OBToolbarIconButton,
    buttonProps,
    500
  );
}());

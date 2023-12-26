/* jslint */
// Create the Navbar Button
isc.Button.create({
  baseStyle: 'navBarButton',
  title: "Copilot",
  class: "OBNavBarTextButton",
  textColor: "#FFFFFF",
  overflow: "visible",
  height: 30,
  width: 80,
  layoutAlign: "center",
  showRollOver: false,
  showFocused: false,
  showDown: false,
  icon: "/etendo/web/images/AI.png",
  borderRadius: "8px !important",
  iconWidth: 14,
  iconHeight: 14,

  // Event handler for button click
  click: function() {
        // Check if the window is already open
        if (window.myWindow && window.myWindow.isVisible()) {
            // If it is already open, simply return without doing anything
            return;
        }

        // Define a function to adjust the window position
        function adjustWindowPosition() {
            var newLeft = Math.max(0, isc.Page.getWidth() - WINDOW_WIDTH);
            var newTop = Math.max(0, isc.Page.getHeight() - WINDOW_HEIGHT);
            myWindow.setLeft(newLeft);
            myWindow.setTop(newTop);
        }

        // Define constants for commonly used values
        WHITE_COLOR = "#FFFFFF"
        GRAY_COLOR = "#666666"
        LIGHT_GRAY_COLOR = "#F2F5F9"
        BUTTON_WIDTH = 80
        BUTTON_HEIGHT = 30
        WINDOW_WIDTH = 425
        WINDOW_HEIGHT = 650

        // Create a new window for Copilot
        myWindow = isc.Window.create({
        width: WINDOW_WIDTH - 10,
        styleName: 'widgetContainer',
        height: WINDOW_HEIGHT,
        left: isc.Page.getWidth() - WINDOW_WIDTH,
        top: isc.Page.getHeight() - WINDOW_HEIGHT,
        canDragReposition: true,
        headerProperties: {
            height: "0px",
        },
        backgroundColor: LIGHT_GRAY_COLOR,
        items: [
                isc.HTMLPane.create({
                  width: "100%",
                  height: "100%",
                  contents: `
                    <html>
                      <head>
                        <style>
                            .close-button {
                              width: 1.4rem;
                              height: 1.4rem;
                              border-radius: 0.5rem;
                            }
                            .close-button-container {
                                padding: 0.3rem;
                                display: flex;
                                justify-content: center;
                                aling-items: center;
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
                                padding: 6px 12px;
                                align-items: center;
                                background-color: white;
                            }
                            .content-header {
                                display: flex;
                                gap: 0.75rem;
                                align-items: center;
                                padding-top: 0.5rem;
                                padding-bottom: 0.5rem;
                            }
                            .copilot-title {
                                font-size: 1.3rem;
                                font-weight: bold;
                                color: #666666;
                            }
                         </style>
                      </head>
                      <body>
                        <div class="container-header">
                            <div class="content-header">
                                <img class="copilot-logotype" src="web/images/copilot.png" alt="Copilot Logotype">
                                <span class="copilot-title">Copilot</span>
                            </div>
                            <div class="close-button-container" onclick="window.parent.myWindow.close()">
                                <img class="close-button" src="web/images/Close.png" alt="Close button">
                            </div>
                        </div>
                        <iframe width="100%" height="575px" src="web/com.etendoerp.copilot.dist" title="Copilot Chat" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
                      </body>
                    </html>
                  `
                })
              ],
    })

    isc.Page.setEvent("resize", adjustWindowPosition);
    adjustWindowPosition();
    myWindow.show();
  }
})

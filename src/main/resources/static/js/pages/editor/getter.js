import BarChartHandler from "./handler/widgets/charts/BarChartHandler.js";
import LineChartHandler from "./handler/widgets/charts/LineChartHandler.js";
import PieChartHandler from "./handler/widgets/charts/PieChartHandler.js";
import MedialAxisHandler from "./handler/widgets/diagrams/MedialAxisHandler.js";
import VoronoiDiagram2DHandler from "./handler/widgets/diagrams/VoronoiDiagram2DHandler.js";
import Map2DHandler from "./handler/widgets/maps/Map2DHandler.js";
import Network2DHandler from "./handler/widgets/networks/Network2DHandler.js";
import StaticIFrameHandler from "./handler/widgets/static/StaticIFrameHandler.js";
import StaticImageHandler from "./handler/widgets/static/StaticImageHandler.js";
import StaticTextHandler from "./handler/widgets/static/StaticTextHandler.js";
import StaticVideoHandler from "./handler/widgets/static/StaticVideoHandler.js";
import ScrollableTableHandler from "./handler/widgets/tables/ScrollableTableHandler.js";
import HighlightTextHandler from "./handler/widgets/text/HighlightTextHandler.js";

import CategoryNumberHandler from "./handler/generators/CategoryNumberHandler.js";
import MapCoordinatesHandler from "./handler/generators/MapCoordinatesHandler.js";
import TextFormattingHandler from "./handler/generators/TextFormattingHandler.js";

const widgets = {
  StaticText: StaticTextHandler,
  StaticImage: StaticImageHandler,
  StaticVideo: StaticVideoHandler,
  StaticIFrame: StaticIFrameHandler,
  ScrollableTable: ScrollableTableHandler,
  BarChart: BarChartHandler,
  PieChart: PieChartHandler,
  LineChart: LineChartHandler,
  VoronoiDiagram2D: VoronoiDiagram2DHandler,
  MedialAxis: MedialAxisHandler,
  HighlightText: HighlightTextHandler,
  Network2D: Network2DHandler,
  Map2D: Map2DHandler,
};

const generators = {
  TextFormatting: TextFormattingHandler,
  CategoryNumber: CategoryNumberHandler,
  MapCoordinates: MapCoordinatesHandler,
};

export default { widgets, generators };

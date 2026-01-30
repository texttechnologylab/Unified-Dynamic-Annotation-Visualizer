import BarChartHandler from "./handler/widgets/charts/BarChartHandler.js";
import LineChartHandler from "./handler/widgets/charts/LineChartHandler.js";
import PieChartHandler from "./handler/widgets/charts/PieChartHandler.js";
import MedialAxisHandler from "./handler/widgets/diagrams/MedialAxisHandler.js";
import VoronoiDiagramHandler from "./handler/widgets/diagrams/VoronoiDiagramHandler.js";
import StaticImageHandler from "./handler/widgets/static/StaticImageHandler.js";
import StaticTextHandler from "./handler/widgets/static/StaticTextHandler.js";
import StaticVideoHandler from "./handler/widgets/static/StaticVideoHandler.js";
import HighlightTextHandler from "./handler/widgets/text/HighlightTextHandler.js";

import CategoryNumberHandler from "./handler/generators/CategoryNumberHandler.js";
import MapCoordinatesHandler from "./handler/generators/MapCoordinatesHandler.js";
import TextFormattingHandler from "./handler/generators/TextFormattingHandler.js";

const widgets = {
  StaticText: StaticTextHandler,
  StaticImage: StaticImageHandler,
  StaticVideo: StaticVideoHandler,
  BarChart: BarChartHandler,
  PieChart: PieChartHandler,
  LineChart: LineChartHandler,
  VoronoiDiagram: VoronoiDiagramHandler,
  MedialAxis: MedialAxisHandler,
  HighlightText: HighlightTextHandler,
};

const generators = {
  TextFormatting: TextFormattingHandler,
  CategoryNumber: CategoryNumberHandler,
  MapCoordinates: MapCoordinatesHandler,
};

export default { widgets, generators };

import BarChartHandler from "./widgets/dynamic/BarChartHandler.js";
import LineChartHandler from "./widgets/dynamic/LineChartHandler.js";
import PieChartHandler from "./widgets/dynamic/PieChartHandler.js";
import MedialAxisHandler from "./widgets/dynamic/MedialAxisHandler.js";
import VoronoiDiagramHandler from "./widgets/dynamic/VoronoiDiagramHandler.js";
import StaticImageHandler from "./widgets/static/StaticImageHandler.js";
import StaticTextHandler from "./widgets/static/StaticTextHandler.js";
import StaticVideoHandler from "./widgets/static/StaticVideoHandler.js";
import HighlightTextHandler from "./widgets/dynamic/HighlightTextHandler.js";

import CategoryNumberHandler from "./generators/CategoryNumberHandler.js";
import MapCoordinatesHandler from "./generators/MapCoordinatesHandler.js";
import TextFormattingHandler from "./generators/TextFormattingHandler.js";

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

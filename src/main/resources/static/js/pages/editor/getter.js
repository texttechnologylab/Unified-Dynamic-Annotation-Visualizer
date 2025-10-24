import BarChartHandler from "./handler/widgets/charts/BarChartHandler.js";
import LineChartHandler from "./handler/widgets/charts/LineChartHandler.js";
import PieChartHandler from "./handler/widgets/charts/PieChartHandler.js";
import Map2DHandler from "./handler/widgets/maps/Map2DHandler.js";
import Network2DHandler from "./handler/widgets/networks/Network2DHandler.js";
import StaticIFrameHandler from "./handler/widgets/static/StaticIFrameHandler.js";
import StaticImageHandler from "./handler/widgets/static/StaticImageHandler.js";
import StaticTextHandler from "./handler/widgets/static/StaticTextHandler.js";
import StaticVideoHandler from "./handler/widgets/static/StaticVideoHandler.js";
import ScrollableTableHandler from "./handler/widgets/tables/ScrollableTableHandler.js";
import HighlightTextHandler from "./handler/widgets/text/HighlightTextHandler.js";

import CategoryNumberColorMappingHandler from "./handler/generators/CategoryNumberColorMappingHandler.js";
import CategoryNumberMappingHandler from "./handler/generators/CategoryNumberMappingHandler.js";
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
  HighlightText: HighlightTextHandler,
  Network2D: Network2DHandler,
  Map2D: Map2DHandler,
};

const generators = {
  TextFormatting: TextFormattingHandler,
  CategoryNumberMapping: CategoryNumberMappingHandler,
  CategoryNumberColorMapping: CategoryNumberColorMappingHandler,
};

export default { widgets, generators };

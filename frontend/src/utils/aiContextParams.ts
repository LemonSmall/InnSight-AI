function compactObject(value: Record<string, any>) {
  const result: Record<string, any> = {}
  Object.entries(value).forEach(([key, item]) => {
    if (item === undefined || item === null || item === '') return
    result[key] = item
  })
  return result
}

export function buildContentAiParams(
  hotel: unknown,
  moduleKey: string,
  params: Record<string, any> = {}
) {
  const selectedParams = compactObject(params)
  const hotelStore = hotel as any
  const surroundingContext = hotelStore?.pendingRecommendation || null
  const weatherContext = hotelStore?.weather || null
  const hotelContext = hotelStore?.config || null
  const occupancyContext = hotelStore?.occupancyImport || null
  const occupancySummary = hotelStore?.occupancySummaryText || ''
  const theme = String(
    selectedParams.theme
      || selectedParams.customTopic
      || selectedParams.topic
      || selectedParams.poster_theme
      || selectedParams.sellingPoints
      || selectedParams.title
      || ''
  )
  const message = String(
    selectedParams.message
      || selectedParams.userQuestion
      || selectedParams.prompt
      || theme
      || `请生成 ${moduleKey} 内容`
  )

  return {
    moduleKey,
    message,
    userQuestion: message,
    requireKnowledge: selectedParams.useKnowledge !== false && selectedParams.requireKnowledge !== false,
    outputStyle: selectedParams.outputStyle || 'final',
    hotelContext,
    weatherContext,
    surroundingContext,
    occupancyContext,
    occupancySummary,
    surroundingContextJson: surroundingContext ? JSON.stringify(surroundingContext) : '',
    occupancyContextJson: occupancyContext ? JSON.stringify(occupancyContext) : '',
    ...selectedParams,
  }
}
